package com.springboot.intellrecipe.voucher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_dead_letter")
public class DeadLetter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageId;
    
    private String exchange;
    
    private String routingKey;
    
    private String queueName;
    
    private String content;
    
    private String reason;
    
    private Integer status;
    
    private Integer retryCount;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}