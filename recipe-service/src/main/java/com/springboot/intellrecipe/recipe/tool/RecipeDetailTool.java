package com.springboot.intellrecipe.recipe.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.springboot.intellrecipe.recipe.entity.Recipe;
import com.springboot.intellrecipe.recipe.mapper.RecipeMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class RecipeDetailTool implements Tool {

    @Resource
    private RecipeMapper recipeMapper;

    @Override
    public String getName() {
        return "get_recipe_detail";
    }

    @Override
    public String getDescription() {
        return "获取食谱的详细信息，包括食材清单、营养分析和烹饪步骤";
    }

    @Override
    public String getParameters() {
        return "{" +
                "\"type\":\"object\"," +
                "\"properties\":{" +
                "\"recipe_id\":{\"type\":\"integer\",\"description\":\"食谱ID\"}" +
                "}," +
                "\"required\":[\"recipe_id\"]" +
                "}";
    }

    @Override
    public Object execute(String arguments, Long userId) {
        JSONObject params = JSONUtil.parseObj(arguments);
        Long recipeId = params.getLong("recipe_id");
        
        Recipe recipe = recipeMapper.selectById(recipeId);
        if (recipe == null) {
            return "食谱不存在";
        }
        
        JSONObject result = new JSONObject();
        result.set("id", recipe.getId());
        result.set("name", recipe.getName());
        result.set("description", recipe.getDescription());
        result.set("cuisine_type", recipe.getCuisineType());
        result.set("taste_profile", recipe.getTasteProfile());
        result.set("calories", recipe.getCalories());
        result.set("protein", recipe.getProtein());
        result.set("fat", recipe.getFat());
        result.set("carbs", recipe.getCarbs());
        result.set("cooking_time", recipe.getCookingTime());
        result.set("difficulty", recipe.getDifficulty());
        result.set("steps", recipe.getSteps());
        result.set("image", recipe.getImage());
        
        return result;
    }
}