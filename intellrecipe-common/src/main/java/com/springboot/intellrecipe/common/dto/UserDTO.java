package com.springboot.intellrecipe.common.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class UserDTO implements Serializable {
    private Long id;
    private String nickname;
    private String icon;
}
