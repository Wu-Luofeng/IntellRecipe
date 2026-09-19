package com.springboot.intellrecipe.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.common.dto.ScrollResult;
import com.springboot.intellrecipe.common.dto.IngredientDTO;
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
     * 同步数据库数据到 ES（带变更探测）。
     * <p>
     * 先算一次全表变更指纹，指纹与上次相同就直接返回，<b>不产生任何 ES 写入</b>。
     * 这是 {@code EsSyncTask} 每 10 秒调用的版本，用来消除无变更时的写入放大。
     *
     * @see #syncEsForce()
     */
    void syncEs();

    /**
     * 强制全量同步到 ES，忽略变更指纹。
     * <p>
     * 用于 ES 索引被误删、数据损坏或手工重建索引的场景 ——
     * 此时指纹没变但索引确实是空的，必须无条件重写一次。
     */
    void syncEsForce();

    /**
     * 获取今日推荐食材（优先读 Redis 缓存，未命中则实时随机查并回填）
     *
     * @return 推荐食材 DTO 列表
     */
    List<IngredientDTO> getRecommend();

    /**
     * 刷新今日推荐食材缓存（定时任务 / 启动预热调用）。
     * 随机选取若干食材写入 Redis，24h TTL。
     */
    void refreshRecommend();

    /**
     * 根据 id 查询食材详情
     */
    IngredientDTO getById(Long id);
}
