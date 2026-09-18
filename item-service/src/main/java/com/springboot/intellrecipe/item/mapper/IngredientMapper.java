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
}
