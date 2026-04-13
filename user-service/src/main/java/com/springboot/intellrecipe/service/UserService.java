package com.springboot.intellrecipe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.UserLoginDTO;
import com.springboot.intellrecipe.common.entity.User;

public interface UserService extends IService<User> {
    Result<String> sendCode(String phone);

    Result<String> login(UserLoginDTO userLoginDTO);
}
