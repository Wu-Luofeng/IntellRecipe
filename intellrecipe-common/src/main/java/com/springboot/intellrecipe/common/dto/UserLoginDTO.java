package com.springboot.intellrecipe.common.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class UserLoginDTO implements Serializable {
    private String phone;
    private String code;
    private String password;
}
