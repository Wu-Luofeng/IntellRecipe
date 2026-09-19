package com.springboot.intellrecipe.item.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.common.dto.IngredientDTO;
import com.springboot.intellrecipe.common.dto.ScrollResult;
import com.springboot.intellrecipe.common.entity.Ingredient;
import com.springboot.intellrecipe.item.mapper.IngredientMapper;
import com.springboot.intellrecipe.item.service.IngredientService;
import com.springboot.intellrecipe.common.utils.CacheClient;
import com.springboot.intellrecipe.common.utils.RedisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.springboot.intellrecipe.item.es.document.IngredientDoc;
import com.springboot.intellrecipe.item.es.repository.IngredientRepository;
import com.springboot.intellrecipe.item.search.IngredientSearchChain;

@Service
public class IngredientServiceImpl extends ServiceImpl<IngredientMapper, Ingredient> implements IngredientService {

    private static final Logger logger = LoggerFactory.getLogger(IngredientServiceImpl.class);

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 检索降级责任链：ES → MySQL ngram 全文索引 → MySQL LIKE 兜底 */
    @Resource
    private IngredientSearchChain searchChain;

    /** 推荐食材数量 */
    private static final int RECOMMEND_SIZE = 8;

    @Autowired(required = false)
    private IngredientRepository ingredientRepository;

    /**
     * 上一次同步到 ES 时全表的变更指纹。
     * <p>
     * {@code null} 表示本次进程启动后还没同步过，此时必须无条件全量写入一次。
     * 用 volatile：定时任务线程写，手动触发接口（/ingredient/sync）可能读。
     */
    private volatile Long lastSyncedFingerprint;

    @Override
    public ScrollResult queryIngredientList(Integer limit, Long lastId) {
        try {
            // 1. 如果不是首页 (lastId != null)，直接查数据库
            if (lastId != null) {
                return queryFromDb(limit, lastId);
            }

            // 2. 首页查询，走通用缓存逻辑（key 含 limit，避免不同分页大小污染同一缓存）
            String cacheKey = RedisConstants.INGREDIENT_FIRSTPAGE_KEY + ":" + limit;
            String lockKey  = RedisConstants.LOCK_INGREDIENT_KEY + ":" + limit;
            return cacheClient.queryWithLogicalExpire(
                    cacheKey,
                    lockKey,
                    ScrollResult.class,
                    () -> queryFromDb(limit, null),
                    30L,
                    TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.error("查询食材列表失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 食材检索。
     * <p>
     * 检索实现已下沉到 {@link IngredientSearchChain} 责任链：
     * ES 全文检索 → MySQL ngram 全文索引 → MySQL LIKE 兜底。
     * 这里只做入参校验，不再关心具体用哪种方式检索、以及降级如何发生。
     * <p>
     * 改动前：ES 超时/异常后直接 catch 到 <code>searchFromDb()</code>，
     * 而该方法是把关键词拆成单字拼一串 <code>LIKE '%x%' AND ...</code>，
     * 前导通配符导致全表扫描，是整个搜索链路的性能洼地。
     */
    @Override
    public List<IngredientDoc> search(String key) {
        if (key == null || key.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return searchChain.search(key);
    }

    /**
     * 将食材数据同步到 ES。
     * <p>
     * <b>为什么要先算指纹再决定写不写：</b>
     * 本方法由 {@code EsSyncTask} 每 10 秒调用一次。如果每次都不加判断地全量
     * {@code saveAll}，即使数据毫无变化，ES 也会为每条记录写入新版本、把旧版本
     * 标记为删除。Lucene 的段文件不可变，标记删除的文档必须等后台 merge 才回收，
     * 于是索引会持续膨胀 —— 实测 52 条数据三天就堆出 135 万条墓碑、206MB 索引。
     * <p>
     * 所以这里先用一条聚合 SQL 拿到全表"变更指纹"：
     * <ul>
     *   <li>指纹未变 → 直接返回，<b>零次 ES 写入</b>（绝大多数轮次走这条路径）</li>
     *   <li>指纹变了 → 全量拉取并写入，同时做一次对账清理</li>
     * </ul>
     * 多出来的成本只有一条聚合查询（52 行的表上微秒级），
     * 换来的是写入量从"每 10 秒 52 次"降到"每次真实变更 52 次"，
     * 因此可以放心保留 10 秒的高频探测，维持数据变更后的热更新体验。
     */
    @Override
    public void syncEs() {
        syncEsInternal(false);
    }

    @Override
    public void syncEsForce() {
        syncEsInternal(true);
    }

    /**
     * @param force {@code true} 时忽略变更指纹，无条件全量重写
     *              （用于 ES 索引被误删或损坏后重建的场景）
     */
    private void syncEsInternal(boolean force) {
        if (ingredientRepository == null) {
            logger.warn("Elasticsearch 已禁用或未装配，跳过同步到 ES");
            return;
        }

        // 1. 变更探测：无变化则零写入直接返回（force 模式跳过该判断）
        Long fingerprint = baseMapper.selectChangeFingerprint();
        if (!force && fingerprint != null && fingerprint.equals(lastSyncedFingerprint)) {
            // 高频路径，用 debug 级别，避免日志被"什么都没发生"刷屏
            logger.debug("食材数据无变更，跳过 ES 同步");
            return;
        }

        // 2. 指纹有变化（或强制）→ 拉取全量并写入 ES
        List<Ingredient> list = list();
        if (list == null || list.isEmpty()) {
            logger.warn("数据库中没有食材数据，无需同步");
            return;
        }
        List<IngredientDoc> docs = list.stream()
                .map(ingredient -> BeanUtil.copyProperties(ingredient, IngredientDoc.class))
                .collect(Collectors.toList());

        try {
            ingredientRepository.saveAll(docs);
            int removed = deleteStaleFromEs(list);
            logger.info("{}同步 {} 条食材数据到 ES{}", force ? "[强制]" : "食材数据变更，已",
                    docs.size(), removed > 0 ? "，对账清理 " + removed + " 条历史文档" : "");
            // 写入成功才推进指纹；失败时保留旧值，下轮会重试
            lastSyncedFingerprint = fingerprint;
        } catch (Exception e) {
            logger.warn("ES 不可用或未启动，跳过同步。搜索仍会走 MySQL 兜底链路。", e);
        }
    }

    /**
     * 对账清理：删除 ES 中存在、但 MySQL 里已经查不到的文档。
     * <p>
     * 为什么需要：管理端删除食材走的是物理删除（{@code DELETE FROM ingredient}），
     * 而 {@code saveAll} 只能新增或覆盖，无法感知"这条数据在库里已经没了"，
     * 结果就是已删除的食材仍然能被搜出来。
     * <p>
     * 只在指纹变化时执行，不参与 10 秒的高频轮询。
     *
     * @param dbList 当前数据库中的全量数据
     * @return 实际清理掉的文档数
     */
    private int deleteStaleFromEs(List<Ingredient> dbList) {
        Set<Long> aliveIds = dbList.stream()
                .map(Ingredient::getId)
                .collect(Collectors.toSet());

        List<IngredientDoc> stale = new ArrayList<>();
        ingredientRepository.findAll().forEach(doc -> {
            if (doc.getId() == null || !aliveIds.contains(doc.getId())) {
                stale.add(doc);
            }
        });

        if (stale.isEmpty()) {
            return 0;
        }
        ingredientRepository.deleteAll(stale);
        return stale.size();
    }

    private ScrollResult queryFromDb(Integer limit, Long lastId) {
        // 1. 准备查询条件
        LambdaQueryWrapper<Ingredient> queryWrapper = new LambdaQueryWrapper<>();
        if (lastId != null) {
            queryWrapper.lt(Ingredient::getId, lastId);
        }
        queryWrapper.orderByDesc(Ingredient::getId).last("limit " + limit);

        // 2. 执行查询
        List<Ingredient> list = list(queryWrapper);

        // 3. 转换为 DTO
        List<IngredientDTO> dtos = list.stream()
                .map(ingredient -> BeanUtil.copyProperties(ingredient, IngredientDTO.class))
                .collect(Collectors.toList());

        // 4. 计算下一次的游标 (minId)
        Long minId = null;
        if (list != null && !list.isEmpty()) {
            minId = list.get(list.size() - 1).getId();
        }

        // 5. 返回结果
        return new ScrollResult(dtos, minId, list == null ? 0 : list.size());
    }

    // ==================== 今日推荐食材 ====================

    @Override
    public List<IngredientDTO> getRecommend() {
        String key = RedisConstants.INGREDIENT_RECOMMEND_KEY;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toList(json, IngredientDTO.class);
            }
        } catch (Exception e) {
            logger.warn("读取推荐食材缓存失败，走 DB 兜底", e);
        }
        // 缓存未命中，实时随机查一次并回填
        List<IngredientDTO> fresh = randomPickFromDb(RECOMMEND_SIZE);
        refreshRecommendCache(fresh);
        return fresh;
    }

    @Override
    public void refreshRecommend() {
        try {
            List<IngredientDTO> fresh = randomPickFromDb(RECOMMEND_SIZE);
            refreshRecommendCache(fresh);
            logger.info("[RecommendTask] 刷新今日推荐食材成功，共 {} 条", fresh.size());
        } catch (Exception e) {
            logger.error("[RecommendTask] 刷新今日推荐食材失败", e);
        }
    }

    @Override
    public IngredientDTO getById(Long id) {
        Ingredient ingredient = super.getById(id);
        if (ingredient == null) {
            return null;
        }
        return BeanUtil.copyProperties(ingredient, IngredientDTO.class);
    }

    /**
     * 从数据库随机选取 n 条食材。
     * 采用「先查全部 id → 随机选 n 个 → 按 id 查详情」的方式，
     * 避免 ORDER BY RAND() 在大数据量下的性能问题。
     */
    private List<IngredientDTO> randomPickFromDb(int n) {
        List<Ingredient> all = list();
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.shuffle(all);
        int size = Math.min(n, all.size());
        return all.subList(0, size).stream()
                .map(ing -> BeanUtil.copyProperties(ing, IngredientDTO.class))
                .collect(Collectors.toList());
    }

    /**
     * 写入推荐缓存（先写临时 key 再 rename，保证原子性，避免缓存击穿空窗）
     */
    private void refreshRecommendCache(List<IngredientDTO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        String key = RedisConstants.INGREDIENT_RECOMMEND_KEY;
        String tmpKey = key + ":tmp:" + System.currentTimeMillis();
        try {
            String json = JSONUtil.toJsonStr(list);
            // 先写临时 key，设置 24h TTL
            stringRedisTemplate.opsForValue().set(tmpKey, json, 24, TimeUnit.HOURS);
            // rename 覆盖正式 key（原子操作）
            stringRedisTemplate.rename(tmpKey, key);
        } catch (Exception e) {
            logger.warn("写入推荐食材缓存失败", e);
            try {
                stringRedisTemplate.delete(tmpKey);
            } catch (Exception ignored) {}
        }
    }
}