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
 * 商城订单明细（下单时商品快照，保证订单可追溯、不受商品后续改价/下架影响）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("trade_order_item")
public class TradeOrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long merchantId;

    private Long productId;

    private String productName;

    private String productImage;

    /** 成交单价（快照） */
    private BigDecimal price;

    private Integer quantity;

    /** 小计 = price * quantity */
    private BigDecimal subtotal;

    private LocalDateTime createTime;
}
