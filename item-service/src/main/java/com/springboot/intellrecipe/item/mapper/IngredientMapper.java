package com.springboot.intellrecipe.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.springboot.intellrecipe.common.entity.Ingredient;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface IngredientMapper extends BaseMapper<Ingredient> {

    /**
     * 物理删除（绕过逻辑删除，真正从数据库中删除记录）
     */
    @Delete("DELETE FROM ingredient WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /**
     * 获取当前最大序列号（物理删除后查实际存在的记录）
     */
    @Select("SELECT COALESCE(MAX(seq_no), 0) FROM ingredient")
    Integer getMaxSeqNo();

    /**
     * 删除后重排序列号：将 seq_no > #{deletedSeqNo} 的记录全部减1，填补空缺
     */
    @Update("UPDATE ingredient SET seq_no = seq_no - 1 WHERE seq_no > #{deletedSeqNo}")
    int shiftSeqNoAfterDelete(@Param("deletedSeqNo") Integer deletedSeqNo);

    /**
     * 获取指定 id 的 seq_no（物理删除前调用）
     */
    @Select("SELECT seq_no FROM ingredient WHERE id = #{id}")
    Integer getSeqNoById(@Param("id") Long id);

    // ==================== 全文检索（ES 降级方案） ====================
    //
    // 为什么不用 LIKE '%x%'：前导通配符使 B+ 树索引彻底失效，只能全表扫描，
    // 数据量一大就是 O(n) 扫描，且无法按相关度排序。
    // 改用 MySQL ngram 全文索引：走倒排索引，并用 MATCH ... AGAINST 的相关度评分排序。
    //
    // 注意事项：
    //  1. 索引必须声明 WITH PARSER ngram（默认 parser 按空格分词，对中文完全无效）
    //     建索引语句见 docker/mysql/init/migration/20260917_ingredient_fulltext.sql
    //  2. deleted = 0 需手写（这里没走 MyBatis-Plus 的逻辑删除拦截器）
    //  3. #{q} 是预编译参数，不存在 SQL 注入；但 BOOLEAN MODE 的操作符由应用层清洗
    //     （见 MysqlFullTextSearchStrategy#toBooleanModeQuery）

    /**
     * ngram 全文检索，按相关度评分倒序返回。
     *
     * @param q     BOOLEAN MODE 查询串（如 {@code +番茄 +茄炒 +炒蛋}）
     * @param limit 返回条数
     */
    @Select("SELECT id, name, image, description, nutrition_value, calories_per100g, " +
            "       MATCH(name, description) AGAINST(#{q} IN BOOLEAN MODE) AS score " +
            "FROM ingredient " +
            "WHERE deleted = 0 " +
            "  AND MATCH(name, description) AGAINST(#{q} IN BOOLEAN MODE) " +
            "ORDER BY score DESC " +
            "LIMIT #{limit}")
    List<Ingredient> searchByFullText(@Param("q") String q, @Param("limit") int limit);

    /**
     * 单字 / 短词兜底检索。
     * <p>
     * 原因：ngram_token_size=2 时，索引里最小的 token 是 2 个字，
     * 单字关键词（"米"、"菜"）无法命中全文索引，只能退回 LIKE。
     * 单字检索语义简单，用 LIKE 可接受，且这类输入占比很低。
     */
    @Select("SELECT id, name, image, description, nutrition_value, calories_per100g " +
            "FROM ingredient " +
            "WHERE deleted = 0 " +
            "  AND (name LIKE CONCAT('%', #{kw}, '%') OR description LIKE CONCAT('%', #{kw}, '%')) " +
            "LIMIT #{limit}")
    List<Ingredient> searchByLike(@Param("kw") String kw, @Param("limit") int limit);

    // ==================== ES 同步的变更探测 ====================
    //
    // EsSyncTask 每 10 秒触发一次同步。如果每次都无条件全量 saveAll，
    // 即使数据一个字节都没变，ES 也会给每条记录写入新版本、把旧版本标记为删除。
    // Lucene 段不可变，标记删除的文档要等后台 merge 才会真正回收，
    // 期间一直占着磁盘和内存。
    //
    // 实测代价：52 条数据 × 每 10 秒一次 ≈ 每天 45 万次写入，
    // 三天堆出 135 万条墓碑文档，索引膨胀到 206MB（真实数据只有 52 条）。
    //
    // 解决思路：先用一条聚合 SQL 算出全表"变更指纹"，指纹没变就直接返回，
    // 一次 ES 写入都不发生。代价只是一条聚合查询 —— 52 行的表上耗时微秒级，
    // 远比写 ES 便宜，因此可以保留 10 秒的高频探测来维持"热加载"体验。

    /**
     * 计算全表变更指纹：对每条记录的 {@code id + update_time} 取 CRC32，再做位异或聚合。
     * <p>
     * 只要发生新增、修改或删除，结果必然改变：
     * <ul>
     *   <li>新增 / 修改 → 多出一条记录或 update_time 变化 → 异或结果变</li>
     *   <li>逻辑删除 → 本质是 UPDATE，会刷新 update_time → 异或结果变</li>
     *   <li>物理删除 → 少一条记录 → 异或结果变</li>
     * </ul>
     * 没有任何变更时结果稳定不变。
     * <p>
     * 用 {@code BIT_XOR} 而非 {@code SUM}：不存在溢出问题，且对行顺序不敏感。
     *
     * @return 变更指纹；表为空时返回 0
     */
    @Select("SELECT COALESCE(BIT_XOR(CRC32(CONCAT(id, '-', IFNULL(update_time, '')))), 0) " +
            "FROM ingredient")
    Long selectChangeFingerprint();
}
