package com.springboot.intellrecipe.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Feign 契约 DTO：核销一张用户券（voucher-service 核销，item-service 结算时调用）
 */
@Data
public class VoucherUseDTO implements Serializable {

    private Long userId;

    /** 券实例ID（voucher_order.id） */
    private Long voucherOrderId;

    /** 订单所属商家ID（券必须属于该商家） */
    private Long shopId;

    /** 关联的商城订单号（核销后回填，用于取消订单自动退券） */
    private String orderNo;

    /** 订单商品总额（元），用于满减门槛校验 */
    private BigDecimal orderAmount;
}
