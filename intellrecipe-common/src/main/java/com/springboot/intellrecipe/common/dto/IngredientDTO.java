package com.springboot.intellrecipe.common.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class IngredientDTO implements Serializable {
    private Long id;
    private String name;
    private String type;
    private String image;
    private String description;
    /** 单位热量文案，与实体 nutritionValue 一致 */
    private String nutritionValue;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
