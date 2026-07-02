package com.springboot.intellrecipe.recipe.dto;

import lombok.Data;

@Data
public class ToolCall {
    private String id;
    private String type;
    private Function function;

    @Data
    public static class Function {
        private String name;
        private String arguments;
    }
}