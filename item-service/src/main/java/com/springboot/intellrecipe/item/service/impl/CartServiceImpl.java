package com.springboot.intellrecipe.item.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.common.dto.CartDTO;
import com.springboot.intellrecipe.common.entity.Cart;
import com.springboot.intellrecipe.common.entity.Product;
import com.springboot.intellrecipe.item.mapper.CartMapper;
import com.springboot.intellrecipe.item.service.CartService;
import com.springboot.intellrecipe.item.service.ProductService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements CartService {

    @Resource
    private ProductService productService;

    @Override
    public void addToCart(Long userId, Long productId, Integer quantity) {
        if (quantity == null || quantity < 1) {
            quantity = 1;
        }
        // 1. 查询商品信息
        Product product = productService.getById(productId);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }
        if (product.getStatus() == null || product.getStatus() != 1) {
            throw new RuntimeException("商品已下架");
        }

        // 2. 查询购物车是否已有该商品
        Cart existing = this.getOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getProductId, productId));

        if (existing != null) {
            // 已存在，累加数量
            existing.setQuantity(existing.getQuantity() + quantity);
            // 更新商品快照（防止商品信息变更）
            existing.setProductName(product.getName());
            existing.setProductImage(product.getImage());
            existing.setProductPrice(product.getPrice());
            existing.setMerchantId(product.getMerchantId());
            this.updateById(existing);
        } else {
            // 不存在，新增
            Cart cart = new Cart()
                    .setUserId(userId)
                    .setProductId(productId)
                    .setMerchantId(product.getMerchantId())
                    .setProductName(product.getName())
                    .setProductImage(product.getImage())
                    .setProductPrice(product.getPrice())
                    .setQuantity(quantity)
                    .setSelected(1);
            this.save(cart);
        }
    }

    @Override
    public List<CartDTO> listMyCart(Long userId) {
        List<Cart> list = this.list(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getCreateTime));
        return list.stream().map(cart -> {
            CartDTO dto = BeanUtil.copyProperties(cart, CartDTO.class);
            // 计算小计
            if (cart.getProductPrice() != null && cart.getQuantity() != null) {
                dto.setSubtotal(cart.getProductPrice().multiply(new BigDecimal(cart.getQuantity())));
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public void updateQuantity(Long userId, Long id, Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new RuntimeException("数量必须大于0");
        }
        this.update(new LambdaUpdateWrapper<Cart>()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, userId)
                .set(Cart::getQuantity, quantity));
    }

    @Override
    public void updateSelected(Long userId, Long id, Integer selected) {
        this.update(new LambdaUpdateWrapper<Cart>()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, userId)
                .set(Cart::getSelected, selected));
    }

    @Override
    public void updateAllSelected(Long userId, Integer selected) {
        this.update(new LambdaUpdateWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .set(Cart::getSelected, selected));
    }

    @Override
    public void removeItem(Long userId, Long id) {
        this.remove(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, userId));
    }

    @Override
    public void removeBatch(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        this.remove(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .in(Cart::getId, ids));
    }

    @Override
    public void clearCart(Long userId) {
        this.remove(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId));
    }
}