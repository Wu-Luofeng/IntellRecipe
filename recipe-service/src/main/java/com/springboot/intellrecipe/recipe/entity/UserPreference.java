package com.springboot.intellrecipe.recipe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_preference")
public class UserPreference {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    private String cuisinePreference;
    private String tastePreference;
    private String avoidIngredients;
    private String healthGoal;
    private Integer dailyCalorieTarget;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}