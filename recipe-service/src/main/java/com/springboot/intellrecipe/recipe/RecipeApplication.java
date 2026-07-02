package com.springboot.intellrecipe.recipe;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@MapperScan("com.springboot.intellrecipe.recipe.mapper")
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {
        "com.springboot.intellrecipe.recipe",
        "com.springboot.intellrecipe.common"
})
public class RecipeApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecipeApplication.class, args);
    }
}