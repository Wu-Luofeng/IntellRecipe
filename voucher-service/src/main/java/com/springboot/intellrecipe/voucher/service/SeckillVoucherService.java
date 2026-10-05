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

    /**
     * 归还 Redis 库存 +1（秒杀重复抢购被 uk_seckill_dedup 拦截时调用）。
     * 场景：Redis Set 丢数据后同一用户二次抢购，第二次 Lua 已多扣一次
     * Redis 库存，DB 事务回滚只能归还 DB 侧，Redis 侧需单独补偿。
     * Set 不需要动——用户本就是已购用户，SADD 幂等。
     * @param voucherId 优惠券ID
     */
    void restoreStock(Long voucherId);
}
