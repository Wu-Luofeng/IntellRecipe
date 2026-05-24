package com.springboot.intellrecipe.diet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@MapperScan("com.springboot.intellrecipe.diet.mapper")
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {
        "com.springboot.intellrecipe.diet",
        "com.springboot.intellrecipe.common"
})
public class DietApplication {
    public static void main(String[] args) {
        SpringApplication.run(DietApplication.class, args);
    }
}
