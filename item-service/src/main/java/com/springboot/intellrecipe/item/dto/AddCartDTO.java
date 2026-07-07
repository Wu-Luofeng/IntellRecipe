package com.springboot.intellrecipe.item.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AddCartDTO implements Serializable {
    private Long productId;
    private Integer quantity;
}