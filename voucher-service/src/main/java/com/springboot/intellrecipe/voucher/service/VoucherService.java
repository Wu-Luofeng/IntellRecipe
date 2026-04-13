package com.springboot.intellrecipe.voucher.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.entity.Voucher;

public interface VoucherService extends IService<Voucher> {
    Result queryVoucherOfShop(Long shopId);
}
