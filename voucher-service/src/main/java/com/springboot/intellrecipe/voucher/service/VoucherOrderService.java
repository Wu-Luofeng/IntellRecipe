package com.springboot.intellrecipe.voucher.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.VoucherOrderDTO;
import com.springboot.intellrecipe.common.entity.VoucherOrder;

public interface VoucherOrderService extends IService<VoucherOrder> {
    /**
     * 统一购买入口 (自动判断普通券/秒杀券)
     * @param voucherId 优惠券ID
     * @return 订单ID
     */
    Long purchaseVoucher(Long voucherId);

    /**
     * 订单创建入口 (自动判断普通券/秒杀券)
     * @param voucherOrderDTO 订单信息
     */
    void createVoucherOrder(VoucherOrderDTO voucherOrderDTO);

    /**
     * 查询当前用户的优惠券列表
     * @return 优惠券列表
     */
    Result queryMyVouchers();
}
