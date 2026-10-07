package com.springboot.intellrecipe.item.es.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;

@Data
@Document(indexName = "ingredient")
public class IngredientDoc {

    @Id
    private Long id;

    /**
     * 食材名称，支持分词搜索
     * 索引用 ik_max_word 细切（"西红柿炒鸡蛋"→西红柿/炒鸡蛋/鸡蛋，最大化召回），
     * 查询用 ik_smart 粗切（避免查询词被过度拆分，提高精度）
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    /**
     * 食材描述，支持分词搜索（与 name 同一 IK 组合）
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String description;

    /**
     * 图片地址，不分词
     */
    @Field(type = FieldType.Keyword, index = false)
    private String image;

    /**
     * 营养值文案，不分词
     */
    @Field(type = FieldType.Keyword, index = false)
    private String nutritionValue;

    /**
     * 每100g热量(千卡)，数值型，不分词
     */
    @Field(type = FieldType.Double, index = false)
    private BigDecimal caloriesPer100g;
}