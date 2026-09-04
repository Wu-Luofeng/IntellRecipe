package com.springboot.intellrecipe.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Feign 契约 DTO：券简况（precheck 返回，受理阶段绑定商家/门槛校验）
 */
@Data
public class VoucherBriefDTO implements Serializable {

    /** 券实例ID */
    private Long voucherOrderId;

    /** 券模板ID */
    private Long voucherId;

    /** 券所属商家ID */
    private Long shopId;

    private String title;

    /** 抵扣金额（分） */
    private Long payValue;

    /** 使用门槛（分） */
    private Long actualValue;

    private Integer type;

    /** 券状态：1待支付 2已支付 3已核销 */
    private Integer status;

    private LocalDateTime expireTime;
}
