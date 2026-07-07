package com.springboot.intellrecipe.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.common.entity.Cart;
import com.springboot.intellrecipe.common.dto.CartDTO;

import java.util.List;

public interface CartService extends IService<Cart> {

    /**
     * 添加商品到购物车
     */
    void addToCart(Long userId, Long productId, Integer quantity);

    /**
     * 查询用户购物车列表
     */
    List<CartDTO> listMyCart(Long userId);

    /**
     * 修改购物车商品数量
     */
    void updateQuantity(Long userId, Long id, Integer quantity);

    /**
     * 修改选中状态
     */
    void updateSelected(Long userId, Long id, Integer selected);

    /**
     * 全选/取消全选
     */
    void updateAllSelected(Long userId, Integer selected);

    /**
     * 删除购物车商品
     */
    void removeItem(Long userId, Long id);

    /**
     * 批量删除购物车商品
     */
    void removeBatch(Long userId, List<Long> ids);

    /**
     * 清空购物车
     */
    void clearCart(Long userId);
}