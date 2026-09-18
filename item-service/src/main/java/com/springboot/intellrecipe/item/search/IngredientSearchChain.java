package com.springboot.intellrecipe.item.search;

import com.springboot.intellrecipe.item.es.document.IngredientDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 搜索降级责任链。
 * <p>
 * 把多个 {@link IngredientSearchStrategy} 按优先级串成一条链，
 * 依次判断 {@code available()} 并执行，失败自动落到下一级。业务代码只调 {@link #search(String)}，
 * 完全不感知"到底用了哪种检索方式"。
 *
 * <h3>为什么用责任链而不是 if-else</h3>
 * 原来的写法是 {@code if (es != null) { try { ... } catch { 降级 } }}，
 * 一旦策略变多（再加本地 Lucene 索引、再加拼音检索），就是一坨嵌套的 if-catch，
 * 而且"某个策略暂时不可用"和"某个策略执行失败"两种逻辑混在一起。
 * 责任链让每个策略只关心自己能不能用、怎么用，链只负责编排和兜底。
 *
 * <h3>降级埋点</h3>
 * 每个策略的成功/失败次数与耗时都会被统计，可通过 {@link #metrics()} 暴露给监控。
 * 降级本身是"静默失败"，如果没有埋点，线上很可能长期走降级而无人知晓。
 */
@Component
public class IngredientSearchChain {

    private static final Logger log = LoggerFactory.getLogger(IngredientSearchChain.class);

    private final List<IngredientSearchStrategy> strategies;

    /**
     * ES 成功但返回空结果时，是否继续用 MySQL 兜底一次。
     * <p>
     * 场景：ES 数据是定时任务同步的（本项目中 10 秒一次），在同步窗口内
     * 新导入的食材在 ES 里还不存在，用户搜不到。开启后 ES 返回空会再查一次 MySQL 确认。
     * <p>
     * 默认关闭——绝大多数空结果是"确实没有匹配"，再查一次是纯粹的浪费。
     */
    @Value("${item.search.fallback-on-empty:false}")
    private boolean fallbackOnEmpty;

    private final Map<String, AtomicLong> successCount = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> failureCount = new ConcurrentHashMap<>();

    public IngredientSearchChain(List<IngredientSearchStrategy> strategies) {
        this.strategies = new ArrayList<>(strategies);
        this.strategies.sort(Comparator.comparingInt(IngredientSearchStrategy::order));
        log.info("[SearchChain] 检索策略链已装配：{}", this.strategies.stream()
                .map(s -> s.name() + "(" + s.order() + ")")
                .collect(java.util.stream.Collectors.joining(" → ")));
    }

    public List<IngredientDoc> search(String key) {
        if (key == null || key.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> attempted = new ArrayList<>();

        for (IngredientSearchStrategy strategy : strategies) {
            if (!strategy.available(key)) {
                continue;
            }
            long start = System.currentTimeMillis();
            try {
                List<IngredientDoc> result = strategy.search(key);
                long cost = System.currentTimeMillis() - start;
                successCount.computeIfAbsent(strategy.name(), k -> new AtomicLong()).incrementAndGet();

                // 策略本身成功了。是否因为"结果为空"继续向下尝试？
                if ((result == null || result.isEmpty()) && fallbackOnEmpty) {
                    attempted.add(strategy.name());
                    log.info("[SearchChain] {} 返回空结果，继续尝试下一策略。key={}", strategy.name(), key);
                    continue;
                }

                if (attempted.isEmpty()) {
                    log.debug("[SearchChain] 命中策略 {}，cost={}ms，hits={}",
                            strategy.name(), cost, result == null ? 0 : result.size());
                } else {
                    log.info("[SearchChain] 降级链生效：{} 均返回空，最终由 {} 兜底。key={}",
                            attempted, strategy.name(), key);
                }
                return result == null ? Collections.emptyList() : result;

            } catch (Exception e) {
                failureCount.computeIfAbsent(strategy.name(), k -> new AtomicLong()).incrementAndGet();
                attempted.add(strategy.name());
                log.warn("[SearchChain] 策略 {} 执行失败，降级到下一级。key={}, reason={}",
                        strategy.name(), key, e.getMessage());
            }
        }

        log.error("[SearchChain] 所有检索策略均不可用，返回空结果。key={}, attempted={}", key, attempted);
        return Collections.emptyList();
    }

    /**
     * 暴露各策略的成功/失败计数，供健康检查或监控采集。
     * 生产环境建议接入 Micrometer 打成 Prometheus 指标，并对"失败数突增"配告警——
     * 长期走降级而不自知是搜索系统最常见的线上隐患。
     */
    public Map<String, Long> metrics() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (IngredientSearchStrategy s : strategies) {
            m.put(s.name() + ".success",
                    successCount.getOrDefault(s.name(), new AtomicLong()).get());
            m.put(s.name() + ".failure",
                    failureCount.getOrDefault(s.name(), new AtomicLong()).get());
        }
        return m;
    }
}
