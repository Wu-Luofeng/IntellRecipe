package com.springboot.intellrecipe.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("merchant")
public class Merchant implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * 商家类型ID (数据库中不存在该字段)
     */
    @TableField(exist = false)
    private Long typeId;

    /**
     * 商家图片 (对应数据库 image 字段)
     */
    @TableField(value = "image")
    private String images;

    /**
     * 商圈 (数据库中不存在该字段)
     */
    @TableField(exist = false)
    private String area;

    private String address;

    /**
     * 经度 (数据库中不存在该字段)
     */
    @TableField(exist = false)
    private Double x;

    /**
     * 纬度 (数据库中不存在该字段)
     */
    @TableField(exist = false)
    private Double y;

    /**
     * 均价 (数据库中不存在该字段)
     */
    @TableField(exist = false)
    private Long avgPrice;

    /**
     * 销量 (数据库中不存在该字段)
     */
    @TableField(exist = false)
    private Integer sold;

    /**
     * 评论数 (数据库中不存在该字段)
     */
    @TableField(exist = false)
    private Integer comments;

    /**
     * 评分 (对应数据库 score 字段, 类型为 decimal(2,1))
     */
    private Double score;

    /**
     * 营业时间 (对应数据库 open_time 字段)
     */
    @TableField(value = "open_time")
    private String openHours;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // 新增字段匹配数据库
    private String phone;

    private String description;

    @TableLogic
    private Integer deleted;
}
