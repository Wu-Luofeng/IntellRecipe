package com.springboot.intellrecipe.voucher.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.common.dto.MyVoucherDTO;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.VoucherOrderDTO;
import com.springboot.intellrecipe.common.entity.Voucher;
import com.springboot.intellrecipe.common.entity.VoucherOrder;
import com.springboot.intellrecipe.common.utils.RedisIdWorker;
import com.springboot.intellrecipe.common.utils.UserHolder;
import com.springboot.intellrecipe.voucher.mapper.VoucherOrderMapper;
import com.springboot.intellrecipe.voucher.service.SeckillVoucherService;
import com.springboot.intellrecipe.voucher.service.VoucherOrderService;
import com.springboot.intellrecipe.voucher.service.VoucherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {

    @Resource
    private SeckillVoucherService seckillVoucherService;

    @Resource
    private VoucherService voucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

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

        // 3. 普通券逻辑：同步落库，避免 MQ 异常时接口成功但无订单
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        long orderId = redisIdWorker.nextId("order");
        VoucherOrderDTO orderDTO = new VoucherOrderDTO();
        orderDTO.setOrderId(orderId);
        orderDTO.setUserId(userId);
        orderDTO.setVoucherId(voucherId);
        orderDTO.setType(0);

        createVoucherOrder(orderDTO);
        return orderId;
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrderDTO voucherOrderDTO) {
        Long userId = voucherOrderDTO.getUserId();
        Long voucherId = voucherOrderDTO.getVoucherId();
        Integer type = voucherOrderDTO.getType();

        if (userId == null) {
            throw new RuntimeException("订单用户ID为空，请重新登录");
        }

        // 幂等：订单已存在则直接返回
        if (getById(voucherOrderDTO.getOrderId()) != null) {
            return;
        }

        // 1. 扣减库存 (秒杀券)
        boolean success = true;
        if (type == 1) {
            success = seckillVoucherService.deductStock(voucherId);
            if (!success) {
                // 抛出异常以触发MQ的消费失败（进入死信队列进行重试或人工干预）
                throw new RuntimeException("扣减库存失败，数据库操作未生效");
            }
        }

        // 2. 保存订单
        if (success) {
            LocalDateTime now = LocalDateTime.now();
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(voucherOrderDTO.getOrderId());
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setPayType(1);
            voucherOrder.setStatus(1);
            voucherOrder.setCreateTime(now);
            voucherOrder.setUpdateTime(now);

            // 设置过期时间 = 创建时间 + 有效期
            Voucher voucher = voucherService.getById(voucherId);
            if (voucher != null && voucher.getValidityDays() != null && voucher.getValidityDays() > 0) {
                voucherOrder.setExpireTime(now.plusDays(voucher.getValidityDays()));
            }

            save(voucherOrder);
        }
    }

    @Override
    public Result queryMyVouchers() {
        Long userId = UserHolder.getUser().getId();

        // 惰性过期：先将已过期的优惠券状态置为0
        baseMapper.updateExpiredVouchers();

        // 查询并过滤掉已过期的优惠券（status=0），前端不展示
        List<MyVoucherDTO> vouchers = baseMapper.selectMyVouchers(userId);
        List<MyVoucherDTO> activeVouchers = vouchers.stream()
                .filter(v -> v.getStatus() != null && v.getStatus() == 1)
                .collect(Collectors.toList());
        return Result.ok(activeVouchers);
    }
}