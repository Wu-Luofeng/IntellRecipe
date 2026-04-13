package com.springboot.intellrecipe.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.common.dto.ScrollResult;
import com.springboot.intellrecipe.common.entity.Ingredient;
import com.springboot.intellrecipe.item.es.document.IngredientDoc;
import java.util.List;

public interface IngredientService extends IService<Ingredient> {
    /**
     * 分页查询食材列表
     * 
     * @param limit  每页条数
     * @param lastId 上一页最后一条的ID (游标)
     * @return 滚动结果
     */
    ScrollResult queryIngredientList(Integer limit, Long lastId);

    /**
     * 搜索食材
     * 
     * @param key 搜索关键词
     * @return 匹配的食材文档列表
     */
    List<IngredientDoc> search(String key);

    /**
     * 同步数据库数据到ES
     */
    void syncEs();
}
