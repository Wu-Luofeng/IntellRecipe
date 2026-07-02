package com.springboot.intellrecipe.recipe.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.UserDTO;
import com.springboot.intellrecipe.common.utils.UserHolder;
import com.springboot.intellrecipe.recipe.dto.ChatRequest;
import com.springboot.intellrecipe.recipe.dto.ChatResponse;
import com.springboot.intellrecipe.recipe.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/recipe/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @PostMapping("/send")
    public Result<ChatResponse> send(@RequestBody ChatRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        
        try {
            ChatResponse response = chatService.chat(request, user.getId());
            return Result.ok(response);
        } catch (Exception e) {
            log.error("Chat failed", e);
            return Result.fail("对话失败: " + e.getMessage());
        }
    }
}