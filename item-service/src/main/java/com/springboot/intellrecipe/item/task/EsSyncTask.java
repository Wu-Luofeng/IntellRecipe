package com.springboot.intellrecipe.item.task;

import com.springboot.intellrecipe.item.service.IngredientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时把食材数据同步到 Elasticsearch。
 * <p>
 * <b>每 10 秒触发一次"探测"，但不是每次都写 ES。</b>
 * 探测本身只跑一条聚合 SQL 算全表变更指纹，只有指纹真的变了（发生增删改）
 * 才会全量写一次索引 —— 具体见
 * {@code IngredientServiceImpl#syncEs()}。
 * </p>
 * <p>
 * 这样设计的原因：如果每次触发都无条件 {@code saveAll}，ES 会为每条记录反复
 * 写入新版本并把旧版本标记删除（Lucene 段不可变，墓碑要等 merge 才回收）。
 * 实测 52 条数据、10 秒一次的频率，三天就堆出 135 万条墓碑、索引膨胀到 206MB。
 * 改成"高频探测 + 按需写入"后，既保留了数据变更后 10 秒内热更新的能力，
 * 又不会在数据没动的时候产生任何索引写入。
 * </p>
 * <p>
 * ES 不可用时这里只记 warn，不影响主流程：搜索会自动降级到
 * MySQL ngram 全文索引（见 IngredientSearchChain）。
 * </p>
 */
@Component
public class EsSyncTask {

    private static final Logger logger = LoggerFactory.getLogger(EsSyncTask.class);

    @Autowired
    private IngredientService ingredientService;

    /**
     * 每 10 秒探测一次。fixedRate 从上次开始执行算起；
     * initialDelay 延迟 30 秒，避免与启动时 ApplicationRunner 的首次同步重叠。
     * <p>
     * 正常情况下（数据无变更）本方法只有一条聚合查询的开销，不打日志。
     */
    @Scheduled(fixedRate = 10 * 1000L, initialDelay = 30 * 1000L)
    public void syncIngredientToEs() {
        try {
            ingredientService.syncEs();
        } catch (Exception e) {
            // 单次失败不中断定时任务，下个周期自动重试
            logger.warn("[ScheduledSync] 定时同步失败，下次继续重试", e);
        }
    }
}