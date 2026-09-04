package com.springboot.intellrecipe.item.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商城订单主表（购物车结算产生，一个订单=一个商家一次结算）
 * 状态: 0=待支付 1=已支付 2=已完成(当前支付即完成) 3=已取消
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("trade_order")
public class TradeOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_PROCESSING = 4;
    public static final int STATUS_FAIL = 5;
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_FINISHED = 2;
    public static final int STATUS_CANCELLED = 3;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务订单号（全局唯一，对外展示 / 追溯） */
    private String orderNo;

    private Long userId;

    private Long merchantId;

    private String merchantName;

    /** 所用优惠券所属商家ID（多商家整单时，用于按该商家小计校验用券门槛） */
    private Long voucherMerchantId;

    /** 商品总额 */
    private BigDecimal totalAmount;

    /** 优惠券抵扣金额 */
    private BigDecimal discountAmount;

    /** 应付金额 = total - discount */
    private BigDecimal payAmount;

    /** 使用的券实例ID（voucher_order.id） */
    private Long voucherOrderId;

    /** 使用的券标题（快照） */
    private String voucherTitle;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;

    /** 结算幂等键（同一结算会话唯一，防重复提交） */
    private String clientToken;

    private Integer status;

    /** 下单失败原因（status=5 时填写） */
    private String failReason;

    private LocalDateTime payTime;

    private LocalDateTime finishTime;

    private LocalDateTime cancelTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
