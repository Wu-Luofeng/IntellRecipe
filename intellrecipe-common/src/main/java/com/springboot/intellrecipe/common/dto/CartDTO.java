package com.springboot.intellrecipe.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CartDTO implements Serializable {
    private Long id;
    private Long userId;
    private Long productId;
    private Long merchantId;
    private String productName;
    private String productImage;
    private BigDecimal productPrice;
    private Integer quantity;
    private Integer selected;
    /** 小计金额 = productPrice * quantity */
    private BigDecimal subtotal;
}