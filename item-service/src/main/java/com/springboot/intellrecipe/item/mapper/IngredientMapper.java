package com.springboot.intellrecipe.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.springboot.intellrecipe.common.entity.Ingredient;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IngredientMapper extends BaseMapper<Ingredient> {
}
