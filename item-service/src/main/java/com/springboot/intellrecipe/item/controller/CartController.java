package com.springboot.intellrecipe.item.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.UserDTO;
import com.springboot.intellrecipe.common.utils.UserHolder;
import com.springboot.intellrecipe.item.dto.AddCartDTO;
import com.springboot.intellrecipe.item.service.CartService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/cart")
public class CartController {

    @Resource
    private CartService cartService;

    /**
     * 添加商品到购物车
     */
    @PostMapping("/add")
    public Result<Void> addToCart(@RequestBody AddCartDTO dto) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        try {
            cartService.addToCart(user.getId(), dto.getProductId(), dto.getQuantity());
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 查询我的购物车列表
     */
    @GetMapping("/list")
    public Result<?> listMyCart() {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        return Result.ok(cartService.listMyCart(user.getId()));
    }

    /**
     * 修改购物车商品数量
     */
    @PutMapping("/quantity/{id}")
    public Result<Void> updateQuantity(@PathVariable Long id, @RequestParam Integer quantity) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        try {
            cartService.updateQuantity(user.getId(), id, quantity);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 修改选中状态
     */
    @PutMapping("/selected/{id}")
    public Result<Void> updateSelected(@PathVariable Long id, @RequestParam Integer selected) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        cartService.updateSelected(user.getId(), id, selected);
        return Result.ok();
    }

    /**
     * 全选/取消全选
     */
    @PutMapping("/selected/all")
    public Result<Void> updateAllSelected(@RequestParam Integer selected) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        cartService.updateAllSelected(user.getId(), selected);
        return Result.ok();
    }

    /**
     * 删除购物车商品
     */
    @DeleteMapping("/{id}")
    public Result<Void> removeItem(@PathVariable Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        cartService.removeItem(user.getId(), id);
        return Result.ok();
    }

    /**
     * 批量删除购物车商品
     */
    @DeleteMapping("/batch")
    public Result<Void> removeBatch(@RequestBody List<Long> ids) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        cartService.removeBatch(user.getId(), ids);
        return Result.ok();
    }

    /**
     * 清空购物车
     */
    @DeleteMapping("/clear")
    public Result<Void> clearCart() {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        cartService.clearCart(user.getId());
        return Result.ok();
    }
}