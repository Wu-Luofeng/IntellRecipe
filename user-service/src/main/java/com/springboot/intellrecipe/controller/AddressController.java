package com.springboot.intellrecipe.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.UserDTO;
import com.springboot.intellrecipe.common.utils.UserHolder;
import com.springboot.intellrecipe.entity.UserAddress;
import com.springboot.intellrecipe.service.AddressService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 收货地址簿（需登录；登录拦截已覆盖 /address/**）
 */
@RestController
@RequestMapping("/address")
public class AddressController {

    @Resource
    private AddressService addressService;

    /** 我的地址列表（默认在前） */
    @GetMapping("/list")
    public Result<?> list() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(addressService.listByUser(user.getId()));
    }

    /** 新增地址（首条自动设默认） */
    @PostMapping
    public Result<Void> create(@RequestBody UserAddress address) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        try {
            addressService.create(user.getId(), address);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 修改地址 */
    @PutMapping
    public Result<Void> update(@RequestBody UserAddress address) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        try {
            addressService.update(user.getId(), address);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 设为默认 */
    @PutMapping("/default/{id}")
    public Result<Void> setDefault(@PathVariable Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        try {
            addressService.setDefault(user.getId(), id);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 删除地址 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        try {
            addressService.remove(user.getId(), id);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }
}
