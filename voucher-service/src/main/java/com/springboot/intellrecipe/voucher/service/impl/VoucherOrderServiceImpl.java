package com.springboot.intellrecipe.voucher.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.common.dto.VoucherOrderDTO;
import com.springboot.intellrecipe.common.entity.Voucher;
import com.springboot.intellrecipe.common.entity.VoucherOrder;
import com.springboot.intellrecipe.common.utils.RedisIdWorker;
import com.springboot.intellrecipe.common.utils.UserHolder;
import com.springboot.intellrecipe.voucher.mapper.VoucherOrderMapper;
import com.springboot.intellrecipe.voucher.service.SeckillVoucherService;
import com.springboot.intellrecipe.voucher.service.VoucherOrderService;
import com.springboot.intellrecipe.voucher.service.VoucherService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {

    @Resource
    private SeckillVoucherService seckillVoucherService;

    @Resource
    private VoucherService voucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public Long purchaseVoucher(Long voucherId) {
        // 1. 查询优惠券
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null) {
            throw new RuntimeException("优惠券不存在！");
        }

        // 2. 秒杀券逻辑
        if (voucher.getType() == 1) {
            return seckillVoucherService.seckillVoucher(voucherId);
        }

        // 3. 普通券逻辑
        long orderId = redisIdWorker.nextId("order");
        VoucherOrderDTO orderDTO = new VoucherOrderDTO();
        orderDTO.setOrderId(orderId);
        orderDTO.setUserId(UserHolder.getUser().getId());
        orderDTO.setVoucherId(voucherId);
        orderDTO.setType(0);

        rabbitTemplate.convertAndSend("voucher.direct", "voucher", orderDTO);

        return orderId;
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrderDTO voucherOrderDTO) {
        Long userId = voucherOrderDTO.getUserId();
        Long voucherId = voucherOrderDTO.getVoucherId();
        Integer type = voucherOrderDTO.getType();

        // 1. 扣减库存 (秒杀券)
        boolean success = true;
        if (type == 1) {
            success = seckillVoucherService.deductStock(voucherId);
        }

        // 2. 保存订单
        if (success) {
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(voucherOrderDTO.getOrderId());
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setPayType(1);
            voucherOrder.setStatus(1);
            voucherOrder.setCreateTime(LocalDateTime.now());
            voucherOrder.setUpdateTime(LocalDateTime.now());
            save(voucherOrder);
        }
    }
}
