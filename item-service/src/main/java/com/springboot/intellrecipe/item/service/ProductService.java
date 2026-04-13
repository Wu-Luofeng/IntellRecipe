package com.springboot.intellrecipe.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.common.entity.Product;
import com.springboot.intellrecipe.common.dto.ScrollResult;
import com.springboot.intellrecipe.common.dto.ProductDTO;

public interface ProductService extends IService<Product> {
    ScrollResult queryProductByMerchant(Long merchantId, Integer limit, Long lastId);

    ProductDTO getProductById(Long id);
}
