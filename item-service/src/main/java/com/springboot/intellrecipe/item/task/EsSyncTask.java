package com.springboot.intellrecipe.item.task;

import com.springboot.intellrecipe.item.service.IngredientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时同步食材数据到 Elasticsearch。
 * <p>
 * 每 30 分钟全量同步一次，确保数据库变更后 ES 索引能及时更新（热加载），
 * 无需重启服务。ES 不可用时跳过，搜索自动走 MySQL 兜底。
 * </p>
 */
@Slf4j
@Component
public class EsSyncTask {

    @Autowired
    private IngredientService ingredientService;

    /**
     * 每 30 分钟同步一次。fixedRate 从上次开始执行算起；
     * initialDelay 延迟 5 分钟，避免与启动时 ApplicationRunner 的同步重叠。
     */
    @Scheduled(fixedRate = 30 * 60 * 1000L, initialDelay = 5 * 60 * 1000L)
    public void syncIngredientToEs() {
        try {
            log.info("[ScheduledSync] 开始定时同步食材数据到 ES...");
            ingredientService.syncEs();
            log.info("[ScheduledSync] 定时同步完成");
        } catch (Exception e) {
            log.warn("[ScheduledSync] 定时同步失败，下次继续重试", e);
        }
    }
}