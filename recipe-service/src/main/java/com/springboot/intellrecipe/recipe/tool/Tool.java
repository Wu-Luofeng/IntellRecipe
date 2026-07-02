package com.springboot.intellrecipe.recipe.tool;

public interface Tool {
    String getName();
    String getDescription();
    String getParameters();
    Object execute(String arguments, Long userId);
}