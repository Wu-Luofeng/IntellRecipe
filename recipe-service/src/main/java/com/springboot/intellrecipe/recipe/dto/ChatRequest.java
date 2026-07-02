package com.springboot.intellrecipe.recipe.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private Long sessionId;
    private String message;
}