package com.springboot.intellrecipe.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.common.entity.Merchant;
import com.springboot.intellrecipe.common.dto.ScrollResult;

public interface MerchantService extends IService<Merchant> {
    /**
     * 滚动查询商家列表
     * 
     * @param limit  每页条数
     * @param lastId 上一页最后一条的ID (游标)
     * @return 滚动结果
     */
    ScrollResult queryMerchantList(Integer limit, Long lastId);
}
