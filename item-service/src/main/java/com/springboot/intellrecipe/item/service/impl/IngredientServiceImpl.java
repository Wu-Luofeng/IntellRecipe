package com.springboot.intellrecipe.item.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.springboot.intellrecipe.item.es.document.IngredientDoc;
import com.springboot.intellrecipe.item.es.repository.IngredientRepository;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

@Slf4j
@Service
public class IngredientServiceImpl extends ServiceImpl<IngredientMapper, Ingredient> implements IngredientService {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 推荐食材数量 */
    private static final int RECOMMEND_SIZE = 8;

    @Autowired(required = false)
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired(required = false)
    private IngredientRepository ingredientRepository;

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
            log.error("查询食材列表失败", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<IngredientDoc> search(String key) {
        if (key == null || key.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        if (elasticsearchRestTemplate == null) {
            return searchFromDb(key);
        }

        try {
            // 构建查询：在 name 和 description 字段中搜索
            NativeSearchQuery query = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.multiMatchQuery(key, "name", "description"))
                    .withPageable(PageRequest.of(0, 20)) // 默认返回前20条
                    .build();

            SearchHits<IngredientDoc> hits = elasticsearchRestTemplate.search(query, IngredientDoc.class);

            // 提取结果
            return hits.getSearchHits().stream()
                    .map(hit -> hit.getContent())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ES搜索异常或超时，触发MySQL兜底查询, keyword: {}", key, e);
            
            return searchFromDb(key);
        }
    }

    private List<IngredientDoc> searchFromDb(String key) {
        LambdaQueryWrapper<Ingredient> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(Ingredient::getName, key)
                .or()
                .like(Ingredient::getDescription, key)
                .last("LIMIT 20");

        List<Ingredient> dbList = list(queryWrapper);

        if (dbList == null || dbList.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return dbList.stream()
                .map(ingredient -> BeanUtil.copyProperties(ingredient, IngredientDoc.class))
                .collect(Collectors.toList());
    }

    @Override
    public void syncEs() {
        // 1. 查询所有数据
        List<Ingredient> list = list();
        if (list == null || list.isEmpty()) {
            log.warn("数据库中没有食材数据，无需同步");
            return;
        }

        // 2. 转换为 Doc
        List<IngredientDoc> docs = list.stream()
                .map(ingredient -> BeanUtil.copyProperties(ingredient, IngredientDoc.class))
                .collect(Collectors.toList());

        if (ingredientRepository == null) {
            log.warn("Elasticsearch 已禁用或未装配，跳过同步到 ES");
            return;
        }
        try {
            ingredientRepository.saveAll(docs);
            log.info("成功同步 {} 条食材数据到 ES", docs.size());
        } catch (Exception e) {
            log.warn("ES 不可用或未启动，跳过同步。keyword 搜索仍会走 MySQL 兜底。", e);
        }
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
            log.warn("读取推荐食材缓存失败，走 DB 兜底", e);
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
            log.info("[RecommendTask] 刷新今日推荐食材成功，共 {} 条", fresh.size());
        } catch (Exception e) {
            log.error("[RecommendTask] 刷新今日推荐食材失败", e);
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
            log.warn("写入推荐食材缓存失败", e);
            try {
                stringRedisTemplate.delete(tmpKey);
            } catch (Exception ignored) {}
        }
    }
}
