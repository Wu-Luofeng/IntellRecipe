package com.springboot.intellrecipe.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.springboot.intellrecipe.recipe.entity.RecipeIngredient;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecipeIngredientMapper extends BaseMapper<RecipeIngredient> {
}