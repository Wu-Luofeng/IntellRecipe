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

    /**
     * 冗余券类型 0:普通券 1:秒杀券（来源于 voucher.type，下单时写入）。
     * 供生成列 dedup_key 判断：仅秒杀券参与 uk_seckill_dedup 一人一单约束，
     * 普通券生成 NULL 逃逸唯一索引，支持重复领取。
     */
    private Integer voucherType;

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
