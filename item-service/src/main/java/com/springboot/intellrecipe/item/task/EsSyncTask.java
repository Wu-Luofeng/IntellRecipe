package com.springboot.intellrecipe.item.task;

import com.springboot.intellrecipe.item.service.IngredientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时同步食材数据到 Elasticsearch。
 * <p>
 * 每 10 秒全量同步一次，确保数据库变更后 ES 索引能及时更新（热加载），
 * 无需重启服务。ES 不可用时跳过，搜索自动走 MySQL 兜底。
 * </p>
 */
@Component
public class EsSyncTask {

    private static final Logger logger = LoggerFactory.getLogger(EsSyncTask.class);

    @Autowired
    private IngredientService ingredientService;

    /**
     * 每 10 秒同步一次。fixedRate 从上次开始执行算起；
     * initialDelay 延迟 30 秒，避免与启动时 ApplicationRunner 的同步重叠。
     */
    @Scheduled(fixedRate = 10 * 1000L, initialDelay = 30 * 1000L)
    public void syncIngredientToEs() {
        try {
            logger.info("[ScheduledSync] 开始定时同步食材数据到 ES...");
            ingredientService.syncEs();
            logger.info("[ScheduledSync] 定时同步完成");
        } catch (Exception e) {
            logger.warn("[ScheduledSync] 定时同步失败，下次继续重试", e);
        }
    }
}