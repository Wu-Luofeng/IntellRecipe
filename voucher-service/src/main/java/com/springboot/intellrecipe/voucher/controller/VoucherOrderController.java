package com.springboot.intellrecipe.voucher.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.voucher.service.VoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/voucher-orders")
public class VoucherOrderController {

    @Resource
    private VoucherOrderService voucherOrderService;

    /**
     * 统一抢购/购买优惠券入口
     * @param voucherId 优惠券ID
     * @return 订单ID
     */
    @PostMapping("purchase/{id}")
    public Result purchaseVoucher(@PathVariable("id") Long voucherId) {
        Long orderId = voucherOrderService.purchaseVoucher(voucherId);
        return Result.ok(orderId);
    }
}
