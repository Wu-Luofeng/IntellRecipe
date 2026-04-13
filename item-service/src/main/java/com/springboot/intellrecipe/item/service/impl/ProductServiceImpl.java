package com.springboot.intellrecipe.item.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.common.dto.ProductDTO;
import com.springboot.intellrecipe.common.dto.ScrollResult;
import com.springboot.intellrecipe.common.entity.Product;
import com.springboot.intellrecipe.item.mapper.ProductMapper;
import com.springboot.intellrecipe.item.service.ProductService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Override
    public ScrollResult queryProductByMerchant(Long merchantId, Integer limit, Long lastId) {
        // 1. 构建查询条件
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getMerchantId, merchantId)
                .eq(Product::getStatus, 1); // 只查上架商品

        // 滚动分页核心逻辑: WHERE id < lastId ORDER BY id DESC LIMIT limit
        if (lastId != null) {
            wrapper.lt(Product::getId, lastId);
        }
        wrapper.orderByDesc(Product::getId)
                .last("LIMIT " + limit);

        // 2. 查询数据库
        List<Product> list = list(wrapper);

        // 3. 转换为 DTO
        List<ProductDTO> dtos = list.stream()
                .map(product -> BeanUtil.copyProperties(product, ProductDTO.class))
                .collect(Collectors.toList());

        // 4. 计算下一次的游标 (minId)
        Long minId = null;
        if (!list.isEmpty()) {
            // 获取列表中最后一个元素的 ID 作为下一次查询的 lastId
            minId = list.get(list.size() - 1).getId();
        }

        // 5. 封装并返回 ScrollResult
        return new ScrollResult(dtos, minId, list.size());
    }

    @Override
    public ProductDTO getProductById(Long id) {
        // 1. 获取 Product 对象
        Product product = this.getById(id);
        if (product == null) {
            return null;
        }

        // 2. 返回 DTO
        return BeanUtil.copyProperties(product, ProductDTO.class);
    }
}
