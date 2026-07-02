package com.springboot.intellrecipe.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.springboot.intellrecipe.recipe.entity.Recipe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface RecipeMapper extends BaseMapper<Recipe> {

    @Select("<script>" +
            "SELECT * FROM recipe WHERE status = 1 AND deleted = 0 " +
            "<if test='avoidIds != null and avoidIds.size() > 0'>" +
            "AND id NOT IN (" +
            "  SELECT recipe_id FROM recipe_ingredient WHERE ingredient_id IN " +
            "  <foreach collection='avoidIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            ")" +
            "</if>" +
            "ORDER BY create_time DESC" +
            "</script>")
    List<Recipe> selectExcludeIngredients(@Param("avoidIds") List<Long> avoidIds);
}