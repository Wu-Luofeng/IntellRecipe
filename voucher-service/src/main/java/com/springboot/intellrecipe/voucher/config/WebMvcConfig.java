package com.springboot.intellrecipe.voucher.config;

import com.springboot.intellrecipe.common.interceptor.LoginInterceptor;
import com.springboot.intellrecipe.common.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. token刷新拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);

        // 2. 登录拦截器 (针对下单接口强制登录)
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/voucher-orders/**") // 仅拦截订单相关接口
                .order(1);
    }
}
