package com.springboot.intellrecipe.item.config;

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
        // 1. token刷新拦截器 (为了获取当前登录用户信息)
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);
        // 2. 登录拦截器 (购物车需要登录)
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/cart/**", "/order/**")
                .order(1);
    }
}
