package com.springboot.intellrecipe.diet.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddEntryDTO {
    /** 食材 ID（必填，后端据此查询食材名称和热量） */
    private Long ingredientId;
    /** 摄入克数 */
    private BigDecimal grams;
    /** 0=早餐 1=午餐 2=晚餐 3=加餐，默认0 */
    private Integer mealType;
}