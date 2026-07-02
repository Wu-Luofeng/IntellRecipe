package com.springboot.intellrecipe.voucher.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.entity.SeckillVoucher;
import com.springboot.intellrecipe.common.entity.Voucher;
import com.springboot.intellrecipe.voucher.mapper.VoucherMapper;
import com.springboot.intellrecipe.voucher.service.SeckillVoucherService;
import com.springboot.intellrecipe.voucher.service.VoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements VoucherService {

    @Resource
    private SeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 1. 查询店铺的所有优惠券
        List<Voucher> vouchers = query().eq("shop_id", shopId).list();
        if (vouchers == null || vouchers.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2. 遍历优惠券，查询秒杀信息并填充到 Voucher 对象
        for (Voucher voucher : vouchers) {
            if (voucher.getType() != null && voucher.getType() == 1) {
                // 秒杀券：从 seckill_voucher 表补充 stock/beginTime/endTime
                SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucher.getId());
                if (seckillVoucher != null) {
                    voucher.setStock(seckillVoucher.getStock());
                    voucher.setBeginTime(seckillVoucher.getBeginTime());
                    voucher.setEndTime(seckillVoucher.getEndTime());
                }
            }
        }
        return Result.ok(vouchers);
    }
}