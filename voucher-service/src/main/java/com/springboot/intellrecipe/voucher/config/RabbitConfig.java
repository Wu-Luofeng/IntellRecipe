package com.springboot.intellrecipe.voucher.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {

    public static final String QUEUE_SECKILL = "seckill.queue";
    public static final String QUEUE_VOUCHER = "voucher.queue";
    public static final String EXCHANGE_DIRECT = "voucher.direct";
    public static final String ROUTING_KEY_SECKILL = "seckill";
    public static final String ROUTING_KEY_VOUCHER = "voucher";

    // 死信交换机和队列名称
    public static final String EXCHANGE_DLX = "dlx.direct";
    public static final String QUEUE_DLX = "dlx.queue";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(EXCHANGE_DIRECT);
    }

    @Bean
    public Queue seckillQueue() {
        Map<String, Object> args = new HashMap<>();
        // 绑定死信交换机
        args.put("x-dead-letter-exchange", EXCHANGE_DLX);
        // 绑定死信路由键 (此处直接将原队列名作为路由键)
        args.put("x-dead-letter-routing-key", QUEUE_SECKILL);
        return new Queue(QUEUE_SECKILL, true, false, false, args);
    }

    @Bean
    public Queue voucherQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EXCHANGE_DLX);
        args.put("x-dead-letter-routing-key", QUEUE_VOUCHER);
        return new Queue(QUEUE_VOUCHER, true, false, false, args);
    }

    @Bean
    public Binding bindingSeckill(Queue seckillQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(seckillQueue).to(directExchange).with(ROUTING_KEY_SECKILL);
    }

    @Bean
    public Binding bindingVoucher(Queue voucherQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(voucherQueue).to(directExchange).with(ROUTING_KEY_VOUCHER);
    }

    // ----------------- 声明死信交换机与队列 -----------------
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(EXCHANGE_DLX);
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue(QUEUE_DLX);
    }

    // 绑定死信队列（无论来源哪个队列的死信，这里统一使用死信路由键进行绑定接收）
    @Bean
    public Binding bindingDlxSeckill() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(QUEUE_SECKILL);
    }

    @Bean
    public Binding bindingDlxVoucher() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(QUEUE_VOUCHER);
    }
}
