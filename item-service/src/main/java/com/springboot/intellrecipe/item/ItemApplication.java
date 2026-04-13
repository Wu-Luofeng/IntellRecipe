package com.springboot.intellrecipe.item;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.springboot.intellrecipe.item.mapper")
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.springboot.intellrecipe.item", "com.springboot.intellrecipe.common"}) // 扫描common包
@EnableAspectJAutoProxy(exposeProxy = true)
public class ItemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItemApplication.class, args);
    }

}
