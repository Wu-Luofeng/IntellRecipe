package com.springboot.intellrecipe.voucher.task;

import com.springboot.intellrecipe.voucher.service.DeadLetterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 死信清扫任务：每小时扫描一次，把创建超过 24 小时仍未处理的死信
 * 自动"放弃 + 补偿"（归还秒杀 Redis 库存、SREM 用户）。
 *
 * 为什么不能在消费失败时就补偿：死信可重放，提前归还库存会导致
 * "重放成功 = 没扣库存卖一张"（反向超卖）。补偿只属于"放弃"时刻。
 */
@Slf4j
@Component
public class DeadLetterSweepTask {

    /** 死信保留期：超过该时长仍未处理的视为彻底放弃 */
    private static final int RETAIN_HOURS = 24;

    @Resource
    private DeadLetterService deadLetterService;

    @Scheduled(fixedDelay = 3600_000, initialDelay = 120_000)
    public void sweep() {
        try {
            int n = deadLetterService.abandonTimeoutDeadLetters(RETAIN_HOURS);
            if (n > 0) {
                log.warn("[DeadLetterSweep] 本轮自动放弃并补偿 {} 条超时死信", n);
            }
        } catch (Exception e) {
            log.error("[DeadLetterSweep] 死信清扫任务异常", e);
        }
    }
}
