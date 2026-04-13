package com.springboot.intellrecipe.item.es.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Document(indexName = "ingredient")
public class IngredientDoc {

    @Id
    private Long id;

    /**
     * 食材名称，支持分词搜索
     * 使用 standard 分词器(单字切分)，无需安装额外插件
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    /**
     * 食材描述，支持分词搜索
     */
    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    /**
     * 图片地址，不分词
     */
    @Field(type = FieldType.Keyword, index = false)
    private String image;

    /**
     * 营养值，不分词
     */
    @Field(type = FieldType.Keyword, index = false)
    private String nutritionValue;
}
