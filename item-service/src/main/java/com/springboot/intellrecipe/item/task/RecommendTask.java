package com.springboot.intellrecipe.item.task;

import com.springboot.intellrecipe.item.service.IngredientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 今日推荐食材定时任务。
 * <p>
 * 1. 服务启动时主动预热一次（避免冷启动 Redis 无数据）；
 * 2. 每天凌晨 3 点刷新推荐列表，24h TTL。
 * </p>
 */
@Component
public class RecommendTask implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(RecommendTask.class);

    @Autowired
    private IngredientService ingredientService;

    /**
     * 启动预热：服务启动后延迟 10 秒执行，避免与 ES 同步等启动任务争抢资源
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            Thread.sleep(10_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        logger.info("[RecommendTask] 服务启动，开始预热今日推荐食材...");
        ingredientService.refreshRecommend();
    }

    /**
     * 每天凌晨 3 点刷新推荐食材
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void refreshDailyRecommend() {
        logger.info("[RecommendTask] 定时任务触发，刷新今日推荐食材...");
        ingredientService.refreshRecommend();
    }
}