package com.springboot.intellrecipe.item.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Resource
    private IngredientRepository ingredientRepository;

    @Override
    public ScrollResult queryIngredientList(Integer limit, Long lastId) {
        try {
            // 1. 如果不是首页 (lastId != null)，直接查数据库
            if (lastId != null) {
                return queryFromDb(limit, lastId);
            }

            // 2. 首页查询，走通用缓存逻辑
            return cacheClient.queryWithLogicalExpire(
                    RedisConstants.INGREDIENT_FIRSTPAGE_KEY,
                    RedisConstants.LOCK_INGREDIENT_KEY,
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

        // 3. 写入 ES
        ingredientRepository.saveAll(docs);
        log.info("成功同步 {} 条食材数据到 ES", docs.size());
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
}
