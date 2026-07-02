package com.springboot.intellrecipe.voucher.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.voucher.service.VoucherOrderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/voucher-orders")
public class VoucherOrderController {

    @Resource
    private VoucherOrderService voucherOrderService;

    /**
     * 查询当前用户的优惠券列表
     * @return 优惠券列表
     */
    @GetMapping("/my")
    public Result queryMyVouchers() {
        return voucherOrderService.queryMyVouchers();
    }

    /**
     * 购买优惠券
     * @param voucherId 优惠券ID
     * @return 订单ID
     */
    @PostMapping("/purchase/{voucherId}")
    public Result purchaseVoucher(@PathVariable("voucherId") Long voucherId) {
        Long orderId = voucherOrderService.purchaseVoucher(voucherId);
        return Result.ok(orderId);
    }
}
