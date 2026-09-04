package com.springboot.intellrecipe.voucher.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.UserDTO;
import com.springboot.intellrecipe.common.utils.UserHolder;

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
        try {
            Long orderId = voucherOrderService.purchaseVoucher(voucherId);
            return Result.ok(orderId);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }
    /**
     * 查询某商家下当前用户“可用”的优惠券（结算抵现页下拉使用）
     * @param shopId 商家ID
     */
    @GetMapping("/usable")
    public Result queryUsable(@RequestParam("shopId") Long shopId) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录");
        }
        return voucherOrderService.queryUsableByShop(user.getId(), shopId);
    }

}
