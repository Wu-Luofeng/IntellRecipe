package com.springboot.intellrecipe.voucher.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

@Slf4j
@Configuration
public class RabbitMqConfig {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void init() {
        // 配置 Publisher Confirm 回调 (当消息到达 Exchange 时触发)
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) {
                return;
            }
            String recordId = correlationData.getId(); // 这里拿到的是 Redis Stream 的消息 ID
            if (ack) {
                log.info("消息成功投递到 Exchange，准备清理 Stream 消息 ID: {}", recordId);
                try {
                    // 收到确认后，从 Redis Stream 中确认并删除该消息，释放内存
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders", "group1", recordId);
                    stringRedisTemplate.opsForStream().delete("stream.orders", recordId);
                    log.info("已成功 XACK 并删除 Stream 中的消息: {}", recordId);
                } catch (Exception e) {
                    log.error("确认/删除 Stream 消息失败", e);
                }
            } else {
                log.error("消息投递到 Exchange 失败，Stream 消息 ID: {}, 原因: {}", recordId, cause);
                // 这里失败了，不进行 XACK。消息会留在 Redis 的 Pending 列表中，等待后续重试！
            }
        });

        // 配置 Publisher Return 回调 (当消息无法路由到 Queue 时触发)
        rabbitTemplate.setReturnsCallback(returnedMessage -> {
            log.error("严重异常：消息无法路由到队列，被退回: 交换机={}, 路由键={}, 消息={}",
                    returnedMessage.getExchange(),
                    returnedMessage.getRoutingKey(),
                    new String(returnedMessage.getMessage().getBody()));
        });
    }
}
