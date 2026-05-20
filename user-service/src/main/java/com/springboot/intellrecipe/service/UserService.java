package com.springboot.intellrecipe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.UserDTO;
import com.springboot.intellrecipe.common.dto.UserLoginDTO;
import com.springboot.intellrecipe.common.dto.UserProfileDTO;
import com.springboot.intellrecipe.common.entity.User;

public interface UserService extends IService<User> {
    Result<String> sendCode(String phone);

    Result<String> login(UserLoginDTO userLoginDTO);

    Result<UserDTO> getMe(Long userId);

    Result<Void> updateProfile(Long userId, UserProfileDTO dto, String token);
}
