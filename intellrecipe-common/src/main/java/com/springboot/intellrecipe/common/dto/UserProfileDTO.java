package com.springboot.intellrecipe.common.dto;

import lombok.Data;

@Data
public class UserProfileDTO {
    private String nickname;
    private String icon;
    private Double height;
    private Double weight;
    private Integer age;
    private Integer gender;
}
