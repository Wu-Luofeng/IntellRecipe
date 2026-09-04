package com.springboot.intellrecipe.item;

import java.util.HashMap;
import java.util.Map;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cloud.openfeign.EnableFeignClients;


import com.springboot.intellrecipe.item.service.IngredientService;

@MapperScan("com.springboot.intellrecipe.item.mapper")
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.springboot.intellrecipe.item", "com.springboot.intellrecipe.common"}) // 扫描common包
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableFeignClients(basePackages = {"com.springboot.intellrecipe.api.client"})

@EnableScheduling
public class ItemApplication {

    private static final Logger logger = LoggerFactory.getLogger(ItemApplication.class);

    /**
     * 服务启动后自动同步食材数据到 ES，避免重启后索引为空导致搜索无结果。
     * 仅当 ES 启用时执行；同步失败不影响服务启动（搜索有 MySQL 兜底）。
     */
    @Bean
    public ApplicationRunner autoSyncEs(IngredientService ingredientService) {
        return args -> {
            try {
                logger.info("[AutoSync] 开始同步食材数据到 Elasticsearch...");
                ingredientService.syncEs();
                logger.info("[AutoSync] 食材数据同步 ES 完成");
            } catch (Exception e) {
                logger.warn("[AutoSync] 食材数据同步 ES 失败，搜索将走 MySQL 兜底", e);
            }
        };
    }

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