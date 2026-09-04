package com.springboot.intellrecipe.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Feign 契约 DTO：退回一张券（商城订单取消时调用）
 */
@Data
public class VoucherReleaseDTO implements Serializable {

    private Long userId;

    /** 券实例ID（voucher_order.id） */
    private Long voucherOrderId;

    /** 关联的商城订单号（校验必须是本订单核销的券才允许退回） */
    private String orderNo;
}
