package com.springboot.intellrecipe.item.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 异步下单 RabbitMQ 基础设施：
 * - 交换机/队列/消息均持久化（durable），保证“受理不丢”；
 * - 消费失败区分两类：瞬时故障 → 延迟重试队列（TTL 30s 后回主队列，最多 3 次）；
 *   业务失败/重试超限 → 拒收（不 requeue）进死信队列留痕，由订单表补偿任务按线性退避兜底；
 * - 消息以 JSON 传输；可靠性由 发布确认(publisher-confirm) + 本地消息表 + 定时补偿 兜底。
 */
@Configuration
public class OrderRabbitConfig {

    public static final String ORDER_EXCHANGE = "trade.order.exchange";
    public static final String ORDER_QUEUE = "trade.order.queue";
    public static final String ORDER_ROUTING_KEY = "trade.order.create";

    public static final String DLX_EXCHANGE = "trade.order.dlx.exchange";
    public static final String DLX_QUEUE = "trade.order.dlq";
    public static final String DLX_ROUTING_KEY = "trade.order.dlx";

    /** 延迟重试队列：TTL 到期后经死信路由回主交换机，模拟 RocketMQ 的 RECONSUME_LATER 重试梯度 */
    public static final String RETRY_QUEUE = "trade.order.retry.queue";
    public static final String HEADER_RETRY_COUNT = "x-retry-count";
    public static final int RETRY_WAIT_SECONDS = 30;
    /** MQ 层最多延迟重投次数；超过后拒收进死信，由订单表补偿任务继续按线性退避重试 */
    public static final int MAX_MQ_RETRY = 3;

    /** 主交换机（direct、durable） */
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    /** 主队列（durable + 死信绑定） */
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderQueue()).to(orderExchange()).with(ORDER_ROUTING_KEY);
    }

    /**
     * 延迟重试队列（durable）：消息在此停留 RETRY_WAIT_SECONDS 秒，
     * TTL 到期后死信路由回主交换机（routing key = ORDER_ROUTING_KEY）重新消费。
     * 投递方式：经默认交换机直发队列名，无需 binding；自定义 header x-retry-count 会随死信保留。
     */
    @Bean
    public Queue orderRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .withArgument("x-message-ttl", RETRY_WAIT_SECONDS * 1000)
                .withArgument("x-dead-letter-exchange", ORDER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_ROUTING_KEY)
                .build();
    }

    /** 死信交换机/队列（留痕，供人工介入或监控） */
    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderDlq() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    @Bean
    public Binding orderDlqBinding() {
        return BindingBuilder.bind(orderDlq()).to(orderDlxExchange()).with(DLX_ROUTING_KEY);
    }

    /** 统一 JSON 消息转换（发送端与 @RabbitListener 容器共用） */
    @Bean
    public MessageConverter orderMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
