package com.springboot.intellrecipe.voucher.listener;

import com.springboot.intellrecipe.voucher.config.RabbitConfig;
import com.springboot.intellrecipe.common.dto.VoucherOrderDTO;
import com.springboot.intellrecipe.voucher.service.VoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class VoucherMqListener {

    @Resource
    private VoucherOrderService voucherOrderService;

    /**
     * 监听秒杀队列和普通队列
     * 两个队列都由同一个业务逻辑处理 (createVoucherOrder 内部会区分type)
     */
    @RabbitListener(queues = {RabbitConfig.QUEUE_SECKILL, RabbitConfig.QUEUE_VOUCHER})
    public void listenVoucherQueue(VoucherOrderDTO voucherOrderDTO) {
        log.info("接收到订单消息: userId={}, voucherId={}, type={}", 
                voucherOrderDTO.getUserId(), voucherOrderDTO.getVoucherId(), voucherOrderDTO.getType());
        
        try {
            voucherOrderService.createVoucherOrder(voucherOrderDTO);
        } catch (Exception e) {
            log.error("处理订单消息失败: {}", voucherOrderDTO, e);
        }
    }
}
