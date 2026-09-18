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
 * D2 修复：待支付订单超时自动关闭。
 * 支付超时（默认 30 分钟）仍未支付的订单自动取消并释放其使用的优惠券，
 * 避免“死单”长期占用与券被一直占用。
 *
 * TODO(调度升级路标)：同 OrderCompensateTask——多实例部署先加 ShedLock，任务家族化后迁 xxl-job。
 */
@Component
public class OrderExpireTask {

    private static final Logger log = LoggerFactory.getLogger(OrderExpireTask.class);

    /** 待支付超时时间（分钟） */
    private static final int EXPIRE_MINUTES = 30;

    @Resource
    private OrderService orderService;

    @Resource
    private TradeOrderMapper tradeOrderMapper;

    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    public void expirePendingOrders() {
        List<TradeOrder> expired = tradeOrderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getStatus, TradeOrder.STATUS_PENDING)
                .lt(TradeOrder::getCreateTime, LocalDateTime.now().minusMinutes(EXPIRE_MINUTES))
                .orderByAsc(TradeOrder::getCreateTime)
                .last("limit 50"));
        if (expired.isEmpty()) {
            return;
        }
        log.info("待支付超时关单扫描：{} 笔待关闭", expired.size());
        for (TradeOrder order : expired) {
            try {
                orderService.cancel(order.getUserId(), order.getOrderNo());
                log.info("超时自动取消订单成功。orderNo={}", order.getOrderNo());
            } catch (Exception e) {
                log.error("超时自动取消订单失败。orderNo={}", order.getOrderNo(), e);
            }
        }
    }
}
