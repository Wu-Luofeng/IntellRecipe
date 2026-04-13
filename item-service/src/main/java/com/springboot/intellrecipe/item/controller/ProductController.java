package com.springboot.intellrecipe.item.controller;

import com.springboot.intellrecipe.common.dto.ProductDTO;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.ScrollResult;
import com.springboot.intellrecipe.item.service.ProductService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;

@RestController
@RequestMapping("/product")
public class ProductController {

    @Resource
    private ProductService productService;

    /**
     * 根据商户ID滚动查询商品列表
     * 
     * @param merchantId 商户ID
     * @param limit      每页条数 (默认10)
     * @param lastId     上一页最后一条ID (游标)
     */
    @GetMapping("/list")
    public Result listByMerchantId(
            @RequestParam("merchantId") Long merchantId,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @RequestParam(value = "lastId", required = false) Long lastId) {
        ScrollResult result = productService.queryProductByMerchant(merchantId, limit, lastId);
        return Result.ok(result);
    }

    /**
     * 根据ID查询商品详情
     */
    @GetMapping("/detail")
    public Result queryProductById(@RequestParam("id") Long id) {
        ProductDTO product = productService.getProductById(id);
        return Result.ok(product);
    }
}
