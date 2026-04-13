package com.springboot.intellrecipe.item.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.ScrollResult;
import com.springboot.intellrecipe.item.service.IngredientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;


@RestController
@RequestMapping("/ingredient")
public class IngredientController {

    @Resource
    private IngredientService ingredientService;

    @GetMapping("/list")
    public Result queryIngredientList(
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @RequestParam(value = "lastId", required = false) Long lastId) {
        ScrollResult result = ingredientService.queryIngredientList(limit, lastId);
        return Result.ok(result);
    }

    @GetMapping("/search")
    public Result searchIngredients(@RequestParam("key") String key) {
        if (key == null || key.trim().isEmpty()) {
            return Result.fail("搜索关键词不能为空");
        }
        return Result.ok(ingredientService.search(key));
    }

    @GetMapping("/sync")
    public Result syncEs() {
        ingredientService.syncEs();
        return Result.ok();
    }
}
