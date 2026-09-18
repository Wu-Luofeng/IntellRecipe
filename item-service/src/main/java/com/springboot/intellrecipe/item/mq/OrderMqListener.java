package com.springboot.intellrecipe.item.mq;

import com.springboot.intellrecipe.item.config.OrderRabbitConfig;
import com.springboot.intellrecipe.item.service.OrderService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 异步下单消费者（手动 ACK，带延迟重试梯度）：
 * 消息体只有订单号（真正数据在 trade_order/明细中）。
 * - 成功 → 把订单由“处理中”推进为“待支付”并清购物车；
 * - 瞬时故障（网络类）→ 投入延迟重试队列，30s 后经死信路由回主队列再消费，最多 3 次；
 * - 业务失败 / 重试超限 → 拒收（不 requeue）进死信留痕：
 *   业务失败已把订单置“失败”，瞬时故障保持“处理中”，由定时补偿（线性退避扫描订单表）兜底。
 */
@Component
public class OrderMqListener {

    private static final Logger log = LoggerFactory.getLogger(OrderMqListener.class);

    @Resource
    private OrderService orderService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private MessageConverter orderMessageConverter;

    @RabbitListener(queues = OrderRabbitConfig.ORDER_QUEUE, ackMode = "MANUAL")
    public void onCreate(String orderNo, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                         @Header(value = OrderRabbitConfig.HEADER_RETRY_COUNT, required = false) Integer retryCount) {
        try {
            orderService.processOrderCreate(orderNo);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            int retried = retryCount == null ? 0 : retryCount;
            // 仅瞬时故障走“延迟重投”：消息进重试队列停留 30s 后死信回主队列；x-retry-count 随消息保留
            if (orderService.isTransientFailure(e) && retried < OrderRabbitConfig.MAX_MQ_RETRY
                    && redeliverWithRetryHeader(orderNo, retried + 1)) {
                try {
                    channel.basicAck(deliveryTag, false);
                    return;
                } catch (Exception ackEx) {
                    // ACK 失败会导致消息重复消费，processOrderCreate 幂等，可接受
                    log.error("重投后 ACK 失败，消息将重复消费（幂等兜底）。orderNo={}", orderNo, ackEx);
                }
            }
            log.error("订单消息处理失败，已拒收进死信，交由订单表定时补偿。orderNo={}", orderNo, e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception nackEx) {
                log.error("订单消息 basicNack 失败", nackEx);
            }
        }
    }

    /** 把订单号消息带 x-retry-count 头投递到延迟重试队列（默认交换机直发队列名，持久化防 MQ 重启丢失） */
    private boolean redeliverWithRetryHeader(String orderNo, int nextRetry) {
        try {
            MessageProperties props = new MessageProperties();
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            Message msg = orderMessageConverter.toMessage(orderNo, props);
            msg.getMessageProperties().setHeader(OrderRabbitConfig.HEADER_RETRY_COUNT, nextRetry);
            rabbitTemplate.send("", OrderRabbitConfig.RETRY_QUEUE, msg);
            log.warn("瞬时故障，{}s 后第 {}/{} 次延迟重投。orderNo={}",
                    OrderRabbitConfig.RETRY_WAIT_SECONDS, nextRetry, OrderRabbitConfig.MAX_MQ_RETRY, orderNo);
            return true;
        } catch (Exception ex) {
            log.error("重试消息投递失败，转由订单表补偿任务兜底。orderNo={}", orderNo, ex);
            return false;
        }
    }
}
