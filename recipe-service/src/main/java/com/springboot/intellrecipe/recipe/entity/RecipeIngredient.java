package com.springboot.intellrecipe.recipe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("recipe_ingredient")
public class RecipeIngredient {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long recipeId;
    private Long ingredientId;
    private BigDecimal amount;
    private String unit;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}