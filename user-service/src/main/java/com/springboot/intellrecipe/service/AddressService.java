package com.springboot.intellrecipe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.entity.UserAddress;

import java.util.List;

/**
 * 收货地址簿服务
 */
public interface AddressService extends IService<UserAddress> {

    /** 我的地址（默认在前，其余按创建倒序） */
    List<UserAddress> listByUser(Long userId);

    /** 新增（首条自动设为默认） */
    void create(Long userId, UserAddress address);

    /** 修改（仅本人） */
    void update(Long userId, UserAddress address);

    /** 设为默认 */
    void setDefault(Long userId, Long id);

    /** 删除；若删除的是默认地址，则自动把最新一条设为默认 */
    void remove(Long userId, Long id);
}
