package com.springboot.intellrecipe.recipe.service;

import com.springboot.intellrecipe.recipe.dto.ChatRequest;
import com.springboot.intellrecipe.recipe.dto.ChatResponse;

public interface ChatService {
    ChatResponse chat(ChatRequest request, Long userId);
}