package com.springboot.intellrecipe.diet.dto;

import com.springboot.intellrecipe.diet.entity.DietLog;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class TodayDietVO {
    private LocalDate date;
    /** 今日总热量（千卡） */
    private BigDecimal totalCalories;
    /** 各条目列表 */
    private List<EntryVO> entries;

    @Data
    public static class EntryVO {
        private Long id;
        private Long ingredientId;
        private String ingredientName;
        private BigDecimal caloriesPer100g;
        private BigDecimal grams;
        /** 实际热量 = caloriesPer100g * grams / 100，保留1位小数 */
        private BigDecimal calories;
        private Integer mealType;
        private String mealTypeName;

        private static final String[] MEAL_NAMES = {"早餐", "午餐", "晚餐", "加餐"};

        public static EntryVO from(DietLog log) {
            EntryVO vo = new EntryVO();
            vo.id = log.getId();
            vo.ingredientId = log.getIngredientId();
            vo.ingredientName = log.getIngredientName();
            vo.caloriesPer100g = log.getCaloriesPer100g();
            vo.grams = log.getGrams();
            int mt = log.getMealType() != null ? log.getMealType() : 0;
            vo.mealType = mt;
            vo.mealTypeName = mt >= 0 && mt < MEAL_NAMES.length ? MEAL_NAMES[mt] : "加餐";
            if (log.getCaloriesPer100g() != null && log.getGrams() != null) {
                vo.calories = log.getCaloriesPer100g()
                        .multiply(log.getGrams())
                        .divide(BigDecimal.valueOf(100), 1, java.math.RoundingMode.HALF_UP);
            } else {
                vo.calories = BigDecimal.ZERO;
            }
            return vo;
        }
    }
}
