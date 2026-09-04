package com.springboot.intellrecipe.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Feign 契约 DTO：结算前校验一张用户券是否可用（voucher-service 只读校验）
 */
@Data
public class VoucherPrecheckDTO implements Serializable {

    private Long userId;

    /** 券实例ID（voucher_order.id） */
    private Long voucherOrderId;
}
