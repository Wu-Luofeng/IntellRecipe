package com.springboot.intellrecipe.common.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductDTO implements Serializable {
    private Long id;
    private String name;
    private String description;
    private String image;
    private BigDecimal price;
    private BigDecimal weight;
    private String unit;
    private Integer stock;
    private Long merchantId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
