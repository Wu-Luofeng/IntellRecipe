package com.springboot.intellrecipe.diet.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddEntryDTO {
    /** 食材 ID */
    private Long ingredientId;
    /** 食材名称 */
    private String ingredientName;
    /** 每 100g 热量（前端从 nutritionValue 字符串中解析，如 "约95千卡/100g" → 95） */
    private BigDecimal caloriesPer100g;
    /** 摄入克数 */
    private BigDecimal grams;
    /** 0=早餐 1=午餐 2=晚餐 3=加餐，默认0 */
    private Integer mealType;
}
