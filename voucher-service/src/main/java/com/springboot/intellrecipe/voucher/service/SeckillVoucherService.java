package com.springboot.intellrecipe.voucher.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.common.entity.SeckillVoucher;

import com.springboot.intellrecipe.common.dto.Result;

public interface SeckillVoucherService extends IService<SeckillVoucher> {
    /**
     * 抢购秒杀券(秒杀入口)
     * @param voucherId 优惠券ID
     * @return 结果
     */
    Long seckillVoucher(Long voucherId);

    /**
     * 扣减库存 (内部调用)
     * @param voucherId 优惠券ID
     * @return 是否扣减成功
     */
    boolean deductStock(Long voucherId);
}
