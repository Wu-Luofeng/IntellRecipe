package com.springboot.intellrecipe.item.mq;

import com.springboot.intellrecipe.item.config.OrderRabbitConfig;
import com.springboot.intellrecipe.item.service.OrderService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 异步下单消费者（手动 ACK）：
 * 消息体只有订单号（真正数据在 trade_order/明细中）。
 * - 成功 → 把订单由“处理中”推进为“待支付”并清购物车；
 * - 失败 → 拒收消息（不自动重试）：业务失败已把订单置“失败”，
 *   瞬时故障保持“处理中”，由定时补偿（扫描订单表）兜底重放。
 */
@Component
public class OrderMqListener {

    private static final Logger log = LoggerFactory.getLogger(OrderMqListener.class);

    @Resource
    private OrderService orderService;

    @RabbitListener(queues = OrderRabbitConfig.ORDER_QUEUE, ackMode = "MANUAL")
    public void onCreate(String orderNo, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            orderService.processOrderCreate(orderNo);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("订单消息处理失败，已拒收，交由订单表定时补偿。orderNo={}", orderNo, e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception nackEx) {
                log.error("订单消息 basicNack 失败", nackEx);
            }
        }
    }
}
