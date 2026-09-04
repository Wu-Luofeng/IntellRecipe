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
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单补偿定时任务（单表模型）：
 * 扫描 status=处理中(4) 且超时未推进的订单（配合 (status,create_time) 索引），
 * 走与 MQ 消费者相同的 processOrderCreate 幂等补做；业务失败会置“下单失败(5)”，
 * 该任务即不再命中；瞬时故障保持处理中下轮再试，确保订单不丢失。
 */
@Component
public class OrderCompensateTask {

    private static final Logger log = LoggerFactory.getLogger(OrderCompensateTask.class);

    /** 下单受理后超过该时长仍未进入“待支付”即触发补偿 */
    private static final int PROCESSING_TIMEOUT_MINUTES = 2;

    @Resource
    private OrderService orderService;

    @Resource
    private TradeOrderMapper tradeOrderMapper;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void compensateProcessingOrders() {
        List<TradeOrder> pendings = tradeOrderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getStatus, TradeOrder.STATUS_PROCESSING)
                .lt(TradeOrder::getCreateTime, LocalDateTime.now().minusMinutes(PROCESSING_TIMEOUT_MINUTES))
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
                log.error("订单补偿执行异常，下轮重试。orderNo={}", order.getOrderNo(), e);
            }
        }
    }
}
