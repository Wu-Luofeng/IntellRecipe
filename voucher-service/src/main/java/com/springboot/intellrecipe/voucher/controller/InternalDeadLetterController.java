package com.springboot.intellrecipe.voucher.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.voucher.service.DeadLetterService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 死信运维 API（/internal 前缀避开登录拦截器）：
 * 人工确认放弃某条死信并触发秒杀补偿（归还 Redis 库存 + SREM 用户）。
 * 幂等：重复调用被状态 CAS 拦截（status 3 已放弃时返回 false）。
 */
@RestController
@RequestMapping("/internal/dead-letters")
public class InternalDeadLetterController {

    @Resource
    private DeadLetterService deadLetterService;

    @PostMapping("/{id}/abandon")
    public Result<Boolean> abandon(@PathVariable("id") Long id) {
        return Result.ok(deadLetterService.abandonDeadLetter(id));
    }
}
