package com.springboot.intellrecipe.voucher.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.VoucherOrderDTO;
import com.springboot.intellrecipe.common.entity.VoucherOrder;
import com.springboot.intellrecipe.api.dto.VoucherReleaseDTO;
import com.springboot.intellrecipe.api.dto.VoucherUseDTO;
import com.springboot.intellrecipe.api.dto.VoucherBriefDTO;
import com.springboot.intellrecipe.api.dto.VoucherPrecheckDTO;

import java.math.BigDecimal;


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
    /**
     * 查询某商家下当前用户“可用”的券（结算抵现用）：status=1/2 且未过期
     * @param userId 用户ID
     * @param shopId 商家ID
     */
    Result queryUsableByShop(Long userId, Long shopId);

    /**
     * 内部 API：商城结算时核销一张用户券（幂等：同 orderNo 重复调用返回原抵扣额）
     * @param useDTO 核销请求
     * @return 抵扣金额（元）
     */
    BigDecimal useVoucher(VoucherUseDTO useDTO);

    /**
     * 内部 API：商城订单取消时退回一张券
     * @param releaseDTO 退券请求
     */
    void releaseVoucher(VoucherReleaseDTO releaseDTO);


    /**
     * 结算前校验并返回一张券的信息（只读，不改状态）
     * @param precheckDTO 校验请求
     * @return 券简况（含 shopId / 门槛 / 有效期）
     */
    VoucherBriefDTO precheck(VoucherPrecheckDTO precheckDTO);

}
