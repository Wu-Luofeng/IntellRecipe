package com.springboot.intellrecipe.item;

import java.util.HashMap;
import java.util.Map;

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
        SpringApplication app = new SpringApplication(ItemApplication.class);
        String esEnabled = System.getenv("ITEM_ELASTICSEARCH_ENABLED");
        if (esEnabled == null) {
            esEnabled = "true";
        }
        if ("false".equalsIgnoreCase(esEnabled.trim())) {
            Map<String, Object> defaults = new HashMap<>();
            defaults.put(
                    "spring.autoconfigure.exclude",
                    "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration");
            app.setDefaultProperties(defaults);
        }
        app.run(args);
    }

}
