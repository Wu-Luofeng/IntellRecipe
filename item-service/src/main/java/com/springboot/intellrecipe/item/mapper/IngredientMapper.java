package com.springboot.intellrecipe.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.springboot.intellrecipe.common.entity.Ingredient;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
}
