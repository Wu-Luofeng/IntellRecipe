package com.springboot.intellrecipe.item.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.common.dto.MerchantDTO;
import com.springboot.intellrecipe.common.dto.ScrollResult;
import com.springboot.intellrecipe.common.entity.Merchant;
import com.springboot.intellrecipe.item.mapper.MerchantMapper;
import com.springboot.intellrecipe.item.service.MerchantService;
import com.springboot.intellrecipe.common.utils.CacheClient;
import com.springboot.intellrecipe.common.utils.RedisConstants;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class MerchantServiceImpl extends ServiceImpl<MerchantMapper, Merchant> implements MerchantService {

    @Resource
    private CacheClient cacheClient;

    @Override
    public ScrollResult queryMerchantList(Integer limit, Long lastId) {
        // 1. 如果不是首页，直接查库
        if (lastId != null) {
            return queryFromDb(limit, lastId);
        }

        // 2. 首页查询，走逻辑过期缓存
        return cacheClient.queryWithLogicalExpire(
                RedisConstants.MERCHANT_FIRSTPAGE_KEY,
                RedisConstants.LOCK_KEY,
                ScrollResult.class,
                () -> queryFromDb(limit, null),
                30L,
                TimeUnit.MINUTES);
    }

    private ScrollResult queryFromDb(Integer limit, Long lastId) {
        // 1. 准备查询条件
        LambdaQueryWrapper<Merchant> queryWrapper = new LambdaQueryWrapper<>();
        if (lastId != null) {
            queryWrapper.lt(Merchant::getId, lastId);
        }
        queryWrapper.orderByDesc(Merchant::getId).last("limit " + limit);

        // 2. 执行查询
        List<Merchant> list = list(queryWrapper);

        // 3. 转换为 DTO
        List<MerchantDTO> dtos = list.stream()
                .map(merchant -> BeanUtil.copyProperties(merchant, MerchantDTO.class))
                .collect(Collectors.toList());

        // 4. 计算下一次的游标
        Long minId = null;
        if (list != null && !list.isEmpty()) {
            minId = list.get(list.size() - 1).getId();
        }

        // 5. 返回结果
        return new ScrollResult(dtos, minId, list == null ? 0 : list.size());
    }
}
