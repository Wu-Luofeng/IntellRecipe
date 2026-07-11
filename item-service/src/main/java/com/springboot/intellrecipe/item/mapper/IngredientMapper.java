package com.springboot.intellrecipe.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.springboot.intellrecipe.common.entity.Ingredient;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IngredientMapper extends BaseMapper<Ingredient> {

    /**
     * 物理删除（绕过逻辑删除，真正从数据库中删除记录）
     */
    @Delete("DELETE FROM ingredient WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
