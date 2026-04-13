package com.springboot.intellrecipe.voucher.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.entity.Voucher;
import com.springboot.intellrecipe.voucher.service.VoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/vouchers")
public class VoucherController {

    @Resource
    private VoucherService voucherService;

    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺ID
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
        return voucherService.queryVoucherOfShop(shopId);
    }

}
