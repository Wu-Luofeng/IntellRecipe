package com.springboot.intellrecipe.item.search;

import cn.hutool.core.bean.BeanUtil;
import com.springboot.intellrecipe.common.entity.Ingredient;
import com.springboot.intellrecipe.item.es.document.IngredientDoc;
import com.springboot.intellrecipe.item.mapper.IngredientMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 三级检索策略：MySQL LIKE 兜底。
 * <p>
 * 走到这里只有两种情况：
 * <ol>
 *   <li>关键词是单字（ngram_token_size=2 时全文索引最小 token 为 2 字，单字命中不了）</li>
 *   <li>ES 未装配/被熔断，且 ngram 全文索引未创建</li>
 * </ol>
 * 相比原实现，这里不再"按单字拆开拼一堆 LIKE AND"，
 * 而是退化成<b>单次 LIKE</b>——原实现把"番茄"拆成
 * {@code name LIKE '%番%' AND name LIKE '%茄%'}，本质还是在全表扫描的基础上多做了几次字符串匹配，
 * 精度提升有限但 SQL 复杂度高；单次 LIKE 更简洁，语义也更直观。
 * <p>
 * 它是最后一道防线，保证"搜索功能永远可用"，哪怕结果质量差一些。
 */
@Component
public class MysqlLikeSearchStrategy implements IngredientSearchStrategy {

    private static final Logger log = LoggerFactory.getLogger(MysqlLikeSearchStrategy.class);

    private static final int SIZE = 20;

    @Resource
    private IngredientMapper ingredientMapper;

    @Override
    public String name() {
        return "MySQL-LIKE";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public boolean available(String key) {
        return key != null && !key.trim().isEmpty();
    }

    @Override
    public List<IngredientDoc> search(String key) {
        String kw = key.trim();
        List<Ingredient> list = ingredientMapper.searchByLike(kw, SIZE);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        log.debug("[Search:MySQL-LIKE] key={}, hits={}", kw, list.size());
        return list.stream()
                .map(i -> BeanUtil.copyProperties(i, IngredientDoc.class))
                .collect(Collectors.toList());
    }
}
