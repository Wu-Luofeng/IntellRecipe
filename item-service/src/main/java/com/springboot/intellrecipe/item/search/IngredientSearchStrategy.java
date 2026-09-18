package com.springboot.intellrecipe.item.search;

import com.springboot.intellrecipe.item.es.document.IngredientDoc;

import java.util.List;

/**
 * 食材搜索策略。
 * <p>
 * 用策略模式把"从哪检索"和"怎么检索"解耦，再用责任链把它们串成一条降级链：
 * <pre>
 *   ① ES 全文检索      （最优：分词准、有相关度排序）
 *   ② MySQL ngram 全文 （次优：倒排索引、有相关度排序）
 *   ③ MySQL LIKE 兜底  （最差：全表扫描，仅在以上都不可用/不适用时使用）
 * </pre>
 * 每个策略自己声明"当前是否可用"，链上按顺序挑第一个可用的执行；
 * 执行中抛异常则由链捕获并自动落到下一个策略，业务代码完全不感知降级过程。
 */
public interface IngredientSearchStrategy {

    /** 策略名称，用于日志与埋点 */
    String name();

    /** 优先级，数值越小越优先（对应 Ordered 的语义） */
    int order();

    /**
     * 当前是否可用。
     * 例如：ES 未装配 / 被熔断 → false；关键词长度不满足 ngram 最小 token → false。
     * 注意这里必须是<b>快速判断</b>，不能有 IO。
     */
    boolean available(String key);

    /** 执行检索，允许抛异常，由责任链负责兜底 */
    List<IngredientDoc> search(String key);
}
