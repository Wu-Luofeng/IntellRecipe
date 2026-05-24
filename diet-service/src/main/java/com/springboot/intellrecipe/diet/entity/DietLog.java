package com.springboot.intellrecipe.diet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("diet_log")
public class DietLog implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long ingredientId;

    private String ingredientName;

    /** 每 100g 热量（千卡），由前端从 nutritionValue 解析后传入 */
    private BigDecimal caloriesPer100g;

    /** 实际摄入克数 */
    private BigDecimal grams;

    /** 0=早餐 1=午餐 2=晚餐 3=加餐 */
    private Integer mealType;

    private LocalDate logDate;

    private LocalDateTime createTime;
}
