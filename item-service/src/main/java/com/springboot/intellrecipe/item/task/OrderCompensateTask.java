package com.springboot.intellrecipe.item.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.springboot.intellrecipe.item.entity.TradeOrder;
import com.springboot.intellrecipe.item.mapper.TradeOrderMapper;
import com.springboot.intellrecipe.item.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 订单补偿定时任务（单表模型 + 线性退避，对标 mcdmc 的重试策略）：
 * 扫描 status=处理中(4) 且退避窗口已到的订单（(status,create_time) 索引 + update_time 退避条件），
 * 走与 MQ 消费者相同的 processOrderCreate 幂等补做。
 * 退避规则：第 n 次失败后（retry_count=n，递增会刷新 update_time），
 * 需再等 (n+1)*BACKOFF_MINUTES 分钟才会被再次扫出；达重试上限由 Service 收敛为终态失败。
 *
 * TODO(调度升级路标，按需实施)：补偿状态全在 DB（订单表=本地消息表 + CAS 幂等），调度器只是触发器，可替换：
 *  ① 多实例部署时先加 ShedLock 防重复触发（幂等已兜底，仅避免浪费）；
 *  ② 补偿任务家族化/需分片与告警时迁移 xxl-job（分片键建议 order_no hash）。
 */
@Component
public class OrderCompensateTask {

    private static final Logger log = LoggerFactory.getLogger(OrderCompensateTask.class);

    /** 退避基数（分钟）：首次补偿 2 分钟后，第 n 次失败后等 (n+1)*2 分钟 */
    private static final int BACKOFF_MINUTES = 2;

    @Resource
    private OrderService orderService;

    @Resource
    private TradeOrderMapper tradeOrderMapper;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void compensateProcessingOrders() {
        // 线性退避：update_time 距今不足 (retry_count+1)*BACKOFF 分钟的订单本轮跳过
        List<TradeOrder> pendings = tradeOrderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getStatus, TradeOrder.STATUS_PROCESSING)
                .apply("update_time < DATE_SUB(NOW(), INTERVAL (IFNULL(retry_count, 0) + 1) * "
                        + BACKOFF_MINUTES + " MINUTE)")
                .orderByAsc(TradeOrder::getCreateTime)
                .last("limit 50"));
        if (pendings.isEmpty()) {
            return;
        }
        log.info("订单补偿扫描：发现 {} 条“处理中”超时订单", pendings.size());
        for (TradeOrder order : pendings) {
            try {
                orderService.processOrderCreate(order.getOrderNo());
            } catch (Exception e) {
                log.error("订单补偿执行异常，按退避策略下轮再试。orderNo={}, retryCount={}",
                        order.getOrderNo(), order.getRetryCount(), e);
            }
        }
    }
}
