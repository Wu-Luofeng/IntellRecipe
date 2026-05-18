package com.springboot.intellrecipe.voucher.listener;

import cn.hutool.json.JSONUtil;
import com.rabbitmq.client.Channel;
import com.springboot.intellrecipe.voucher.config.RabbitConfig;
import com.springboot.intellrecipe.common.dto.VoucherOrderDTO;
import com.springboot.intellrecipe.voucher.entity.DeadLetter;
import com.springboot.intellrecipe.voucher.service.DeadLetterService;
import com.springboot.intellrecipe.voucher.service.VoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
public class VoucherMqListener {

    @Resource
    private VoucherOrderService voucherOrderService;

    @Resource
    private DeadLetterService deadLetterService;

    /**
     * 监听秒杀队列和普通队列
     * 两个队列都由同一个业务逻辑处理 (createVoucherOrder 内部会区分type)
     */
    @RabbitListener(queues = { RabbitConfig.QUEUE_SECKILL, RabbitConfig.QUEUE_VOUCHER })
    public void listenVoucherQueue(VoucherOrderDTO voucherOrderDTO, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("接收到订单消息: userId={}, voucherId={}, type={}",
                voucherOrderDTO.getUserId(), voucherOrderDTO.getVoucherId(), voucherOrderDTO.getType());

        try {
            // 核心下单逻辑
            voucherOrderService.createVoucherOrder(voucherOrderDTO);

            // 消费成功，手动确认 ACK
            channel.basicAck(deliveryTag, false);
            log.info("订单消息消费成功，已发送 ACK: orderId={}", voucherOrderDTO.getOrderId());

        } catch (DataIntegrityViolationException e) {
            // 幂等性处理：捕获到主键重复异常，说明该订单已经处理过了，无需重复处理
            log.warn("检测到重复的订单消息，触发幂等处理，直接丢弃: orderId={}", voucherOrderDTO.getOrderId());
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("处理订单消息失败: orderId={}", voucherOrderDTO.getOrderId(), e);
            // 消费失败，拒绝确认并将其丢入死信队列 (DLQ)
            // requeue=false: 不重新入原队列，因为原队列配了 DLX，所以会自动转发到死信队列
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 监听死信队列
     * 当常规队列消费失败被 Nack 且 requeue=false 时，消息会被路由到这里
     */
    @RabbitListener(queues = RabbitConfig.QUEUE_DLX)
    public void listenDlxQueue(VoucherOrderDTO voucherOrderDTO, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.error("【严重告警】接收到死信订单消息，需人工介入处理: orderId={}, userId={}, voucherId={}",
                voucherOrderDTO.getOrderId(), voucherOrderDTO.getUserId(), voucherOrderDTO.getVoucherId());

        try {
            // 将死信消息存入数据库表 tb_dead_letter
            DeadLetter dlq = new DeadLetter();
            dlq.setMessageId(String.valueOf(voucherOrderDTO.getOrderId())); // 用订单ID暂代消息ID
            dlq.setExchange(RabbitConfig.EXCHANGE_DLX);
            dlq.setQueueName(RabbitConfig.QUEUE_DLX);
            dlq.setContent(JSONUtil.toJsonStr(voucherOrderDTO));
            dlq.setReason("正常消费队列(seckill/voucher)处理失败，被流转至死信队列");
            dlq.setStatus(0); // 0-未处理
            dlq.setRetryCount(0);
            dlq.setCreateTime(LocalDateTime.now());
            dlq.setUpdateTime(LocalDateTime.now());

            deadLetterService.save(dlq);
            log.info("死信订单已成功落库: tb_dead_letter, orderId={}", voucherOrderDTO.getOrderId());

            // 记录完毕后确认，避免死信队列堆积
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("死信队列落库异常", e);
            // 这里如果落库都失败了，只能Nack丢回死信队列，或者直接打印到严重错误日志中（比如日志文件监控报警）
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
