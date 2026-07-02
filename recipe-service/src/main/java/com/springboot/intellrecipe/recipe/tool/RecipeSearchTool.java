package com.springboot.intellrecipe.recipe.tool;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.springboot.intellrecipe.recipe.entity.Recipe;
import com.springboot.intellrecipe.recipe.mapper.RecipeMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecipeSearchTool implements Tool {

    @Resource
    private RecipeMapper recipeMapper;

    @Override
    public String getName() {
        return "search_recipes";
    }

    @Override
    public String getDescription() {
        return "根据菜系、口味、食材等条件搜索食谱，支持营养约束";
    }

    @Override
    public String getParameters() {
        return "{" +
                "\"type\":\"object\"," +
                "\"properties\":{" +
                "\"cuisine\":{\"type\":\"string\",\"description\":\"菜系类型，如：川菜、粤菜、家常菜\"}," +
                "\"taste\":{\"type\":\"string\",\"description\":\"口味偏好，如：辣、清淡、甜\"}," +
                "\"max_calories\":{\"type\":\"integer\",\"description\":\"最大热量(kcal)\"}," +
                "\"min_protein\":{\"type\":\"integer\",\"description\":\"最小蛋白质(g)\"}" +
                "}" +
                "}";
    }

    @Override
    public Object execute(String arguments, Long userId) {
        JSONObject params = JSONUtil.parseObj(arguments);
        
        LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Recipe::getStatus, 1);
        
        String cuisine = params.getStr("cuisine");
        if (cuisine != null && !cuisine.isEmpty()) {
            wrapper.like(Recipe::getCuisineType, cuisine);
        }
        
        String taste = params.getStr("taste");
        if (taste != null && !taste.isEmpty()) {
            wrapper.like(Recipe::getTasteProfile, taste);
        }
        
        Integer maxCalories = params.getInt("max_calories");
        if (maxCalories != null) {
            wrapper.le(Recipe::getCalories, maxCalories);
        }
        
        Integer minProtein = params.getInt("min_protein");
        if (minProtein != null) {
            wrapper.ge(Recipe::getProtein, minProtein);
        }
        
        wrapper.last("LIMIT 10");
        
        List<Recipe> recipes = recipeMapper.selectList(wrapper);
        
        List<JSONObject> results = new ArrayList<>();
        for (Recipe recipe : recipes) {
            JSONObject obj = new JSONObject();
            obj.set("id", recipe.getId());
            obj.set("name", recipe.getName());
            obj.set("cuisine_type", recipe.getCuisineType());
            obj.set("taste_profile", recipe.getTasteProfile());
            obj.set("calories", recipe.getCalories());
            obj.set("protein", recipe.getProtein());
            obj.set("cooking_time", recipe.getCookingTime());
            results.add(obj);
        }
        
        return results;
    }
}