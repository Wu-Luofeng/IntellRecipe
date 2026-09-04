package com.springboot.intellrecipe.voucher.controller;

import com.springboot.intellrecipe.api.dto.VoucherReleaseDTO;
import com.springboot.intellrecipe.api.dto.VoucherUseDTO;
import com.springboot.intellrecipe.api.dto.VoucherBriefDTO;
import com.springboot.intellrecipe.api.dto.VoucherPrecheckDTO;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.voucher.service.VoucherOrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 内部 API（Feign 契约 VoucherClient 的落点）：
 * 供 item-service 结算下单/取消订单时调用；前缀 /internal/voucher 避开登录拦截器。
 * 用户身份由请求体 userId 携带（调用方已通过登录拦截器校验）。
 */
@RestController
@RequestMapping("/internal/voucher")
public class InternalVoucherController {

    @Resource
    private VoucherOrderService voucherOrderService;

    /**
     * 核销一张用户券（商城结算）
     */
    @PostMapping("/use")
    public Result<BigDecimal> use(@RequestBody VoucherUseDTO dto) {
        try {
            BigDecimal discount = voucherOrderService.useVoucher(dto);
            return Result.ok(discount);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 退回一张券（商城订单取消）
     */
    @PostMapping("/release")
    public Result<Void> release(@RequestBody VoucherReleaseDTO dto) {
        try {
            voucherOrderService.releaseVoucher(dto);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 结算前校验一张券并返回简况（只读）
     */
    @PostMapping("/precheck")
    public Result<VoucherBriefDTO> precheck(@RequestBody VoucherPrecheckDTO dto) {
        try {
            return Result.ok(voucherOrderService.precheck(dto));
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }
}
