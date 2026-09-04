package com.springboot.intellrecipe.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.entity.UserAddress;
import com.springboot.intellrecipe.mapper.UserAddressMapper;
import com.springboot.intellrecipe.service.AddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress> implements AddressService {

    private static final String VALID_MSG = "请填写完整的收货人/电话/地址";

    @Override
    public List<UserAddress> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId));
    }

    @Override
    public void create(Long userId, UserAddress address) {
        if (address == null || StrUtil.hasBlank(address.getReceiverName(),
                address.getReceiverPhone(), address.getReceiverAddress())) {
            throw new RuntimeException(VALID_MSG);
        }
        long count = count(new LambdaQueryWrapper<UserAddress>().eq(UserAddress::getUserId, userId));
        UserAddress toSave = new UserAddress()
                .setUserId(userId)
                .setReceiverName(address.getReceiverName().trim())
                .setReceiverPhone(address.getReceiverPhone().trim())
                .setReceiverAddress(address.getReceiverAddress().trim())
                .setIsDefault(count == 0 ? 1 : 0);
        save(toSave);
    }

    @Override
    public void update(Long userId, UserAddress address) {
        if (address == null || address.getId() == null) {
            throw new RuntimeException("参数缺失");
        }
        if (StrUtil.hasBlank(address.getReceiverName(),
                address.getReceiverPhone(), address.getReceiverAddress())) {
            throw new RuntimeException(VALID_MSG);
        }
        boolean updated = update(new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getId, address.getId())
                .eq(UserAddress::getUserId, userId)
                .set(UserAddress::getReceiverName, address.getReceiverName().trim())
                .set(UserAddress::getReceiverPhone, address.getReceiverPhone().trim())
                .set(UserAddress::getReceiverAddress, address.getReceiverAddress().trim()));
        if (!updated) {
            throw new RuntimeException("地址不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long id) {
        update(new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .set(UserAddress::getIsDefault, 0));
        boolean updated = update(new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getId, id)
                .eq(UserAddress::getUserId, userId)
                .set(UserAddress::getIsDefault, 1));
        if (!updated) {
            throw new RuntimeException("地址不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long id) {
        UserAddress addr = getOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, id).eq(UserAddress::getUserId, userId));
        if (addr == null) {
            throw new RuntimeException("地址不存在");
        }
        removeById(id);
        if (addr.getIsDefault() != null && addr.getIsDefault() == 1) {
            UserAddress latest = getOne(new LambdaQueryWrapper<UserAddress>()
                    .eq(UserAddress::getUserId, userId)
                    .orderByDesc(UserAddress::getId)
                    .last("limit 1"));
            if (latest != null) {
                update(new LambdaUpdateWrapper<UserAddress>()
                        .eq(UserAddress::getId, latest.getId())
                        .set(UserAddress::getIsDefault, 1));
            }
        }
    }
}
