package com.springboot.intellrecipe.recipe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.UserDTO;
import com.springboot.intellrecipe.common.utils.UserHolder;
import com.springboot.intellrecipe.recipe.entity.Recipe;
import com.springboot.intellrecipe.recipe.entity.UserPreference;
import com.springboot.intellrecipe.recipe.mapper.RecipeMapper;
import com.springboot.intellrecipe.recipe.mapper.UserPreferenceMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * Agent 数据契约接口：供 Python agent-service 调用（Python 不直连数据库）。
 * 鉴权：走 /recipe/** 既有的登录拦截器，agent 透传用户 token。
 * 契约详见 agent-service/README.md 与 agent-service/config.yaml 的 api.endpoints。
 */
@RestController
@RequestMapping("/recipe/agent")
public class AgentDataController {

    @Resource
    private UserPreferenceMapper preferenceMapper;

    @Resource
    private RecipeMapper recipeMapper;

    /**
     * GET /recipe/agent/preference
     * 当前用户的饮食偏好；无记录时返回默认空对象（userId 已填充）。
     */
    @GetMapping("/preference")
    public Result<?> preference() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        UserPreference pref = preferenceMapper.selectOne(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, user.getId())
                .last("limit 1"));
        if (pref == null) {
            pref = new UserPreference();
            pref.setUserId(user.getId());
        }
        return Result.ok(pref);
    }

    /**
     * GET /recipe/agent/recipes
     * 菜品条件查询（全部上架中的菜品）：
     * keyword（名称/描述模糊）、cuisine、taste（模糊）、minCalories/maxCalories、minProtein、limit。
     */
    @GetMapping("/recipes")
    public Result<?> recipes(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String cuisine,
                             @RequestParam(required = false) String taste,
                             @RequestParam(required = false) Double minCalories,
                             @RequestParam(required = false) Double maxCalories,
                             @RequestParam(required = false) Double minProtein,
                             @RequestParam(defaultValue = "20") Integer limit) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        LambdaQueryWrapper<Recipe> qw = new LambdaQueryWrapper<>();
        qw.eq(Recipe::getStatus, 1);
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(Recipe::getName, keyword).or().like(Recipe::getDescription, keyword));
        }
        if (cuisine != null && !cuisine.isBlank()) {
            qw.like(Recipe::getCuisineType, cuisine);
        }
        if (taste != null && !taste.isBlank()) {
            qw.like(Recipe::getTasteProfile, taste);
        }
        if (minCalories != null) {
            qw.ge(Recipe::getCalories, minCalories.intValue());
        }
        if (maxCalories != null) {
            qw.le(Recipe::getCalories, maxCalories.intValue());
        }
        if (minProtein != null) {
            qw.ge(Recipe::getProtein, BigDecimal.valueOf(minProtein));
        }
        qw.last("limit " + Math.min(Math.max(limit, 1), 100));
        return Result.ok(recipeMapper.selectList(qw));
    }
}
