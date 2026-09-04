package com.springboot.intellrecipe.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("voucher_order")
public class VoucherOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long userId;

    private Long voucherId;

    private Integer payType;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    private LocalDateTime useTime;

    private LocalDateTime refundTime;

    /** 结算抵现核销时关联的商城订单号（voucher_order → trade_order 追溯） */
    private String usedOrderNo;

    /** 结算抵现核销时关联的商城订单主键ID */
    private Long usedOrderId;

    private LocalDateTime updateTime;
}
