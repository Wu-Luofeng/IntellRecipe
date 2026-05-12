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
        String esEnabled = System.getProperty("ITEM_ELASTICSEARCH_ENABLED");
        if (esEnabled == null || esEnabled.isEmpty()) {
            esEnabled = System.getenv("ITEM_ELASTICSEARCH_ENABLED");
        }
        if (esEnabled == null) {
            esEnabled = "true";
        }
        if ("false".equalsIgnoreCase(esEnabled.trim())) {
            // System property beats application.yml and fixes "elasticsearchTemplate" missing when ES is off.
            System.setProperty("spring.data.elasticsearch.repositories.enabled", "false");
            Map<String, Object> defaults = new HashMap<>();
            defaults.put(
                    "spring.autoconfigure.exclude",
                    "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration");
            defaults.put("spring.data.elasticsearch.repositories.enabled", Boolean.FALSE);
            app.setDefaultProperties(defaults);
        }
        app.run(args);
    }

}
