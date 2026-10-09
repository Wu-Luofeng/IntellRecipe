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

    /**
     * 秒杀放弃补偿（原子 Lua）：归还 Redis 库存 +1 并 SREM 已购用户。
     * 场景：秒杀消息彻底放弃（死信超时/人工确认）时，归还 Lua 已扣的资产，
     * 让用户可以重新参与抢购。库存 key 不存在时只 SREM（DB 重初始化路径值是准的）。
     */
    boolean rollbackSeckill(Long voucherId, Long userId);
}
