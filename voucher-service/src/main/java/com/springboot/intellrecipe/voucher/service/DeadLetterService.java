package com.springboot.intellrecipe.voucher.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.voucher.entity.DeadLetter;

public interface DeadLetterService extends IService<DeadLetter> {
    
    /**
     * 补偿重试某条死信消息
     */
    boolean retryDeadLetter(Long id);

    /**
     * 放弃一条死信消息并执行秒杀补偿（归还 Redis 库存 + SREM 用户）。
     * CAS 抢占（status 0/2 -> 3）防重复补偿；仅 type=1（秒杀）触发 Redis 归还。
     * 放弃后的死信禁止重放——重放会导致 Redis 库存虚高。
     */
    boolean abandonDeadLetter(Long id);

    /**
     * 自动放弃超时死信（createTime 超过 hours 小时仍未处理的）。
     * 返回成功放弃并补偿的条数。
     */
    int abandonTimeoutDeadLetters(int hours);
}