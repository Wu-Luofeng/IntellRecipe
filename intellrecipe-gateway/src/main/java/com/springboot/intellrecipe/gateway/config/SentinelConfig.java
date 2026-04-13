package com.springboot.intellrecipe.gateway.config;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.Collections;

@Configuration
public class SentinelConfig implements GlobalFilter, Ordered {

    @PostConstruct
    public void doInit() {
        initParamFlowRules();
    }

    /**
     * 初始化热点参数限流规则
     * 资源名：user_rate_limit
     * 参数索引：0 (第一个参数，即 Token)
     * 阈值：5 QPS
     */
    private void initParamFlowRules() {
        ParamFlowRule rule = new ParamFlowRule("user_rate_limit")
                .setParamIdx(0) // 对第 0 个参数限流
                .setCount(5)    // QPS 阈值
                .setDurationInSec(1); // 统计窗口时长 1秒
        ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
    }

    /**
     * 全局过滤器：手动进行限流检查
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 获取 Token
        String token = exchange.getRequest().getHeaders().getFirst("authorization");
        if (token == null || token.isEmpty()) {
            // 如果没有 Token，放行（或者你可以选择拒绝）
            return chain.filter(exchange);
        }

        Entry entry = null;
        try {
            // 2. 手动调用 Sentinel，传入资源名和参数(token)
            // 务必确保 EntryType.IN 被导入
            entry = SphU.entry("user_rate_limit", EntryType.IN, 1, token);
            // 3. 如果通过，继续执行
            return chain.filter(exchange);
        } catch (BlockException ex) {
            // 4. 如果被限流，抛出异常或处理
            return handleBlockException(exchange);
        } finally {
            if (entry != null) {
                entry.exit(1, token);
            }
        }
    }

    private Mono<Void> handleBlockException(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\": 429, \"message\": \"请求过于频繁，请稍后再试！\", \"success\": false}";
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        return -1; // 优先级
    }
}