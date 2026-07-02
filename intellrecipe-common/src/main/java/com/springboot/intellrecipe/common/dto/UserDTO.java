package com.springboot.intellrecipe.common.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserDTO implements Serializable {
    private Long id;
    private String phone;
    private String nickname;
    private String icon;
    private Double height;
    private Double weight;
    private Integer age;
    private Integer gender;
    private LocalDateTime createTime;
}
