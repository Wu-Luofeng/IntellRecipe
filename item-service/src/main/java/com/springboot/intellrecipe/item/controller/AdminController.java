package com.springboot.intellrecipe.item.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.entity.Ingredient;
import com.springboot.intellrecipe.common.entity.Merchant;
import com.springboot.intellrecipe.common.entity.Product;
import com.springboot.intellrecipe.common.utils.RedisConstants;
import com.springboot.intellrecipe.item.mapper.IngredientMapper;
import com.springboot.intellrecipe.item.mapper.MerchantMapper;
import com.springboot.intellrecipe.item.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;

/**
 * 管理端控制器 —— 食材/商家/商品的增删改查
 * 不做登录鉴权，仅供内部管理使用
 */
@Slf4j
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Resource
    private IngredientMapper ingredientMapper;
    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private ProductMapper productMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // ==================== 缓存清理工具方法 ====================

    /**
     * 清理食材相关缓存：
     * - cache:ingredient:firstpage:* （首页列表缓存，逻辑过期）
     * - cache:ingredient:recommend （今日推荐缓存）
     */
    private void clearIngredientCache() {
        try {
            Set<String> keys = stringRedisTemplate.keys(RedisConstants.INGREDIENT_FIRSTPAGE_KEY + ":*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
            stringRedisTemplate.delete(RedisConstants.INGREDIENT_RECOMMEND_KEY);
            log.info("[Admin] 食材缓存已清理");
        } catch (Exception e) {
            log.warn("[Admin] 清理食材缓存失败", e);
        }
    }

    /**
     * 清理商家相关缓存：
     * - cache:merchant:firstpage:* （首页列表缓存，逻辑过期）
     */
    private void clearMerchantCache() {
        try {
            Set<String> keys = stringRedisTemplate.keys(RedisConstants.MERCHANT_FIRSTPAGE_KEY + ":*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
            log.info("[Admin] 商家缓存已清理");
        } catch (Exception e) {
            log.warn("[Admin] 清理商家缓存失败", e);
        }
    }

    /**
     * 清理商品相关缓存（目前商品无独立缓存，预留扩展）
     */
    private void clearProductCache() {
        // 商品目前无 Redis 缓存，预留扩展位
        log.info("[Admin] 商品缓存清理（无缓存，跳过）");
    }

    // ==================== 食材管理 ====================

    /**
     * 新增食材
     * 必选: name, caloriesPer100g
     * 可选: description, nutritionValue, image
     */
    @PostMapping("/ingredient")
    public Result addIngredient(@RequestBody Ingredient ingredient) {
        if (ingredient.getName() == null || ingredient.getName().trim().isEmpty()) {
            return Result.fail("食材名称不能为空");
        }
        if (ingredient.getCaloriesPer100g() == null) {
            return Result.fail("热量不能为空");
        }
        // 检查名称是否重复
        Ingredient exist = ingredientMapper.selectOne(
                new LambdaQueryWrapper<Ingredient>().eq(Ingredient::getName, ingredient.getName()));
        if (exist != null) {
            return Result.fail("食材名称已存在");
        }
        ingredientMapper.insert(ingredient);
        clearIngredientCache();
        return Result.ok(ingredient);
    }

    /**
     * 修改食材
     */
    @PutMapping("/ingredient")
    public Result updateIngredient(@RequestBody Ingredient ingredient) {
        if (ingredient.getId() == null) {
            return Result.fail("ID不能为空");
        }
        ingredientMapper.updateById(ingredient);
        clearIngredientCache();
        return Result.ok();
    }

    /**
     * 删除食材（物理删除）
     */
    @DeleteMapping("/ingredient/{id}")
    public Result deleteIngredient(@PathVariable Long id) {
        ingredientMapper.physicalDeleteById(id);
        clearIngredientCache();
        return Result.ok();
    }

    /**
     * 查询全部食材（分页）
     */
    @GetMapping("/ingredient/list")
    public Result listIngredient(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        LambdaQueryWrapper<Ingredient> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(Ingredient::getName, keyword);
        }
        wrapper.orderByDesc(Ingredient::getId);
        Page<Ingredient> p = ingredientMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.ok(p.getRecords(), p.getTotal());
    }

    // ==================== 商家管理 ====================

    /**
     * 新增商家
     * 必选: name
     * 可选: address, phone, image, score, description, openTime
     */
    @PostMapping("/merchant")
    public Result addMerchant(@RequestBody Merchant merchant) {
        if (merchant.getName() == null || merchant.getName().trim().isEmpty()) {
            return Result.fail("商家名称不能为空");
        }
        if (merchant.getScore() == null) {
            merchant.setScore(5.0);
        }
        merchantMapper.insert(merchant);
        clearMerchantCache();
        return Result.ok(merchant);
    }

    /**
     * 修改商家
     */
    @PutMapping("/merchant")
    public Result updateMerchant(@RequestBody Merchant merchant) {
        if (merchant.getId() == null) {
            return Result.fail("ID不能为空");
        }
        merchantMapper.updateById(merchant);
        clearMerchantCache();
        return Result.ok();
    }

    /**
     * 删除商家（物理删除）
     */
    @DeleteMapping("/merchant/{id}")
    public Result deleteMerchant(@PathVariable Long id) {
        merchantMapper.physicalDeleteById(id);
        clearMerchantCache();
        return Result.ok();
    }

    /**
     * 查询全部商家（分页）
     */
    @GetMapping("/merchant/list")
    public Result listMerchant(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(Merchant::getName, keyword);
        }
        wrapper.orderByDesc(Merchant::getId);
        Page<Merchant> p = merchantMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.ok(p.getRecords(), p.getTotal());
    }

    /**
     * 查询全部商家（不分页，给下拉选择用）
     */
    @GetMapping("/merchant/all")
    public Result allMerchant() {
        List<Merchant> list = merchantMapper.selectList(
                new LambdaQueryWrapper<Merchant>().orderByDesc(Merchant::getId));
        return Result.ok(list);
    }

    // ==================== 商品管理 ====================

    /**
     * 新增商品
     * 必选: merchantId, name, price
     * 可选: image, description, weight, unit, status
     */
    @PostMapping("/product")
    public Result addProduct(@RequestBody Product product) {
        if (product.getMerchantId() == null) {
            return Result.fail("商家ID不能为空");
        }
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            return Result.fail("商品名称不能为空");
        }
        if (product.getPrice() == null) {
            return Result.fail("商品价格不能为空");
        }
        if (product.getStatus() == null) {
            product.setStatus(1);
        }
        productMapper.insert(product);
        clearProductCache();
        return Result.ok(product);
    }

    /**
     * 修改商品
     */
    @PutMapping("/product")
    public Result updateProduct(@RequestBody Product product) {
        if (product.getId() == null) {
            return Result.fail("ID不能为空");
        }
        productMapper.updateById(product);
        clearProductCache();
        return Result.ok();
    }

    /**
     * 删除商品（物理删除）
     */
    @DeleteMapping("/product/{id}")
    public Result deleteProduct(@PathVariable Long id) {
        productMapper.physicalDeleteById(id);
        clearProductCache();
        return Result.ok();
    }

    /**
     * 查询商品列表（按商家筛选，分页）
     */
    @GetMapping("/product/list")
    public Result listProduct(
            @RequestParam(value = "merchantId", required = false) Long merchantId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size,
            @RequestParam(value = "keyword", required = false) String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            wrapper.eq(Product::getMerchantId, merchantId);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(Product::getName, keyword);
        }
        wrapper.orderByDesc(Product::getId);
        Page<Product> p = productMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.ok(p.getRecords(), p.getTotal());
    }
}