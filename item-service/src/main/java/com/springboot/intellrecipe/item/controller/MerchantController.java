package com.springboot.intellrecipe.item.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.item.service.MerchantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/merchant")
public class MerchantController {

    @Resource
    private MerchantService merchantService;

    /**
     * 滚动查询商家列表
     * 
     * @param lastId 上一次查询结果的最后一条ID (游标)
     * @param limit  每页条数
     */
    @GetMapping("/list")
    public Result queryMerchantList(
            @RequestParam(value = "lastId", required = false) Long lastId,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        return Result.ok(merchantService.queryMerchantList(limit, lastId));
    }
}
