package com.springboot.intellrecipe.recipe.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.springboot.intellrecipe.recipe.entity.UserPreference;
import com.springboot.intellrecipe.recipe.mapper.UserPreferenceMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class PreferenceTool implements Tool {

    @Resource
    private UserPreferenceMapper userPreferenceMapper;

    @Override
    public String getName() {
        return "manage_preference";
    }

    @Override
    public String getDescription() {
        return "管理用户的饮食偏好和忌口。action 为 get 时获取偏好，为 save 时保存偏好";
    }

    @Override
    public String getParameters() {
        return "{" +
                "\"type\":\"object\"," +
                "\"properties\":{" +
                "\"action\":{\"type\":\"string\",\"enum\":[\"get\",\"save\"],\"description\":\"操作类型\"}," +
                "\"preference_type\":{\"type\":\"string\",\"enum\":[\"like_taste\",\"dislike_taste\",\"like_ingredient\",\"dislike_ingredient\"],\"description\":\"偏好类型\"}," +
                "\"value\":{\"type\":\"string\",\"description\":\"偏好值\"}" +
                "}" +
                "}";
    }

    @Override
    public Object execute(String arguments, Long userId) {
        JSONObject params = JSONUtil.parseObj(arguments);
        String action = params.getStr("action", "get");
        
        if ("get".equals(action)) {
            return getUserPreference(userId);
        } else if ("save".equals(action)) {
            return saveUserPreference(userId, params);
        }
        
        return "未知操作";
    }
    
    private Object getUserPreference(Long userId) {
        UserPreference pref = userPreferenceMapper.selectOne(
            new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId)
        );
        
        if (pref == null) {
            return "用户暂无偏好记录";
        }
        
        JSONObject result = new JSONObject();
        result.set("cuisine_preference", pref.getCuisinePreference());
        result.set("taste_preference", pref.getTastePreference());
        result.set("avoid_ingredients", pref.getAvoidIngredients());
        return result;
    }
    
    private Object saveUserPreference(Long userId, JSONObject params) {
        String type = params.getStr("preference_type");
        String value = params.getStr("value");
        
        if (type == null || value == null) {
            return "缺少必要参数";
        }
        
        UserPreference pref = userPreferenceMapper.selectOne(
            new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId)
        );
        
        if (pref == null) {
            pref = new UserPreference();
            pref.setUserId(userId);
        }
        
        if ("like_taste".equals(type)) {
            JSONArray arr = pref.getTastePreference() != null 
                ? JSONUtil.parseArray(pref.getTastePreference()) 
                : new JSONArray();
            if (!arr.contains(value)) {
                arr.add(value);
                pref.setTastePreference(arr.toString());
            }
        } else if ("dislike_taste".equals(type)) {
            JSONArray arr = pref.getTastePreference() != null 
                ? JSONUtil.parseArray(pref.getTastePreference()) 
                : new JSONArray();
            String dislikeKey = "!" + value;
            if (!arr.contains(dislikeKey)) {
                arr.add(dislikeKey);
                pref.setTastePreference(arr.toString());
            }
        } else if ("like_ingredient".equals(type) || "dislike_ingredient".equals(type)) {
            JSONArray arr = pref.getAvoidIngredients() != null 
                ? JSONUtil.parseArray(pref.getAvoidIngredients()) 
                : new JSONArray();
            String key = "dislike_ingredient".equals(type) ? "!" + value : value;
            if (!arr.contains(key)) {
                arr.add(key);
                pref.setAvoidIngredients(arr.toString());
            }
        }
        
        if (pref.getId() == null) {
            userPreferenceMapper.insert(pref);
        } else {
            userPreferenceMapper.updateById(pref);
        }
        
        return "偏好已保存";
    }
}