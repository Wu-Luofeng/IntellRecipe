package com.springboot.intellrecipe.item.search;

import com.springboot.intellrecipe.item.es.document.IngredientDoc;
// 注意：ES 7.17 中 TimeValue 已从 org.elasticsearch.common.unit 迁移到 org.elasticsearch.core；
// 但 Spring Data Elasticsearch 4.x 的 withTimeout() 签名接收的是 java.time.Duration，故这里直接用 Duration
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 一级检索策略：Elasticsearch 全文检索。
 * <p>
 * 相比原实现增加了两件事：
 * <ol>
 *   <li><b>超时控制</b>：原来的查询没有设置任何超时，ES 抖动时请求会一直挂着，
 *       直到 HTTP 客户端超时才抛异常。现在显式设置 500ms，超时立即失败并降级。</li>
 *   <li><b>熔断保护</b>：连续失败达到阈值后直接短路，不再访问 ES，
 *       避免"每个请求都等超时"把服务线程池拖垮（见 {@link EsCircuitBreaker}）。</li>
 * </ol>
 */
@Component
public class EsSearchStrategy implements IngredientSearchStrategy {

    private static final Logger log = LoggerFactory.getLogger(EsSearchStrategy.class);

    private static final int SIZE = 20;

    /**
     * ES 未启用时该 Bean 为 null（配合 ITEM_ELASTICSEARCH_ENABLED 开关），
     * 这是"低配服务器不装 ES 也能跑"的关键。
     */
    @Autowired(required = false)
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Value("${item.search.es-timeout-ms:500}")
    private long esTimeoutMs;

    /** 每 10s 窗口内至少 10 次调用、失败率 > 50% 则熔断 30s */
    private final EsCircuitBreaker breaker = EsCircuitBreaker.defaultConfig();

    @Override
    public String name() {
        return "ES";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean available(String key) {
        return elasticsearchRestTemplate != null && breaker.allowRequest();
    }

    @Override
    public List<IngredientDoc> search(String key) {
        if (elasticsearchRestTemplate == null) {
            throw new IllegalStateException("ES 未装配");
        }
        if (!breaker.allowRequest()) {
            // 熔断打开，直接短路，不发起任何网络请求
            throw new IllegalStateException("ES 熔断器处于 " + breaker.getState() + "，短路降级");
        }
        breaker.markHalfOpenPassed();

        try {
            NativeSearchQuery query = new NativeSearchQueryBuilder()
                    .withQuery(QueryBuilders.multiMatchQuery(key, "name", "description")
                            .operator(Operator.AND))
                    .withPageable(PageRequest.of(0, SIZE))
                    // 服务端超时：ES 超时后返回已收集到的分片结果，避免无限等待
                    .withTimeout(Duration.ofMillis(esTimeoutMs))
                    .build();

            SearchHits<IngredientDoc> hits = elasticsearchRestTemplate.search(query, IngredientDoc.class);
            List<IngredientDoc> result = hits.getSearchHits().stream()
                    .map(hit -> hit.getContent())
                    .collect(Collectors.toList());

            breaker.recordSuccess();
            return result;

        } catch (Exception e) {
            breaker.recordFailure();
            log.warn("[Search:ES] ES 检索失败（{}），key={}", e.getMessage(), key);
            throw e;
        }
    }

    /** 供监控/健康检查暴露状态 */
    public String breakerState() {
        return breaker.getState().name();
    }
}
