package com.springboot.intellrecipe.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.UserLoginDTO;
import com.springboot.intellrecipe.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 发送验证码
     */
    @PostMapping("/code")
    public Result<String> sendCode(@RequestParam("phone") String phone) {
        log.info("接收到发送验证码请求，手机号: {}", phone);
        return userService.sendCode(phone);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("接收到登录请求: {}", userLoginDTO);
        return userService.login(userLoginDTO);
    }
}
