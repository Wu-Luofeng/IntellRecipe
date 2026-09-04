package com.springboot.intellrecipe.item.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单状态流转日志（可追溯：订单何时由谁从哪个状态变到哪个状态）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("trade_order_status_log")
public class TradeOrderStatusLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String orderNo;

    /** 原状态 */
    private Integer fromStatus;

    /** 变更后状态 */
    private Integer orderStatus;

    /** 操作人/系统（用户昵称或 system） */
    private String operator;

    private String remark;

    private LocalDateTime createTime;
}
