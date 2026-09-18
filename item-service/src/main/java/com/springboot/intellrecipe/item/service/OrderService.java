package com.springboot.intellrecipe.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.item.dto.CreateOrderDTO;
import com.springboot.intellrecipe.item.dto.OrderDetailDTO;
import com.springboot.intellrecipe.item.entity.TradeOrder;

import java.util.List;

/**
 * 商城订单服务（单表模型）：
 * /order/create 同步“受理”→ 写订单(status=处理中)+明细并落库后，仅把订单号发给 MQ；
 * MQ 消费/定时补偿用 processOrderCreate 完成核销、状态推进（待支付）与清购物车。
 * 订单表即本地消息表：定时扫描 status=处理中 且超时的订单补偿，确保不丢。
 */
public interface OrderService extends IService<TradeOrder> {

    /**
     * 结算受理（异步下单）：校验并锁定商品、写订单(处理中)+明细，投递 MQ 后立即返回订单号
     *
     * @return 订单号列表（可能因跨商家拆单返回多个）
     */
    List<String> createOrders(Long userId, CreateOrderDTO dto);

    /**
     * 异步下单执行体（MQ 消费者与定时补偿共用）：幂等，仅处理“处理中”订单
     *
     * @param orderNo 订单号
     */
    void processOrderCreate(String orderNo);

    /**
     * 瞬时故障判定：MQ 消费者据此决定“延迟重投”还是“拒收进死信 + 订单表补偿”
     */
    boolean isTransientFailure(Throwable t);

    /**
     * 查询订单（轮询受理结果：data.status = 处理中/待支付/…）
     */
    TradeOrder queryOrderStatus(Long userId, String orderNo);

    /**
     * 我的订单列表
     */
    List<TradeOrder> queryMyOrders(Long userId, Integer status);

    /**
     * 订单详情（主单 + 明细 + 状态时间线 = 可追溯）
     */
    OrderDetailDTO queryOrderDetail(Long userId, String orderNo);

    /**
     * 支付（当前“支付即完成”：0→1 支付成功，随后自动 1→2 完成，为后续派送预留中间态）
     */
    void pay(Long userId, String orderNo);

    /**
     * 取消订单（仅待支付可取消；用券订单自动退券）
     */
    void cancel(Long userId, String orderNo);
}
