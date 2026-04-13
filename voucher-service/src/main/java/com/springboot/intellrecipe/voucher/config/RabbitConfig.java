package com.springboot.intellrecipe.voucher.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String QUEUE_SECKILL = "seckill.queue";
    public static final String QUEUE_VOUCHER = "voucher.queue";
    public static final String EXCHANGE_DIRECT = "voucher.direct";
    public static final String ROUTING_KEY_SECKILL = "seckill";
    public static final String ROUTING_KEY_VOUCHER = "voucher";


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
        return new Queue(QUEUE_SECKILL);
    }
    
    @Bean
    public Queue voucherQueue() {
        return new Queue(QUEUE_VOUCHER);
    }

    @Bean
    public Binding bindingSeckill(Queue seckillQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(seckillQueue).to(directExchange).with(ROUTING_KEY_SECKILL);
    }
    
    @Bean
    public Binding bindingVoucher(Queue voucherQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(voucherQueue).to(directExchange).with(ROUTING_KEY_VOUCHER);
    }
}
