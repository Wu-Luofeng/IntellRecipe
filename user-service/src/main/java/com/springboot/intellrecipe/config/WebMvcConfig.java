package com.springboot.intellrecipe.config;

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
                                .addPathPatterns("/**") // 拦截所有请求
                                .order(0); // 优先级最高

                // 2. 登录拦截器
                registry.addInterceptor(new LoginInterceptor())
                                .excludePathPatterns(
                                                "/shop/**",
                                                "/voucher/**",
                                                "/shop-type/**",
                                                "/upload/**",
                                                "/blog/hot",
                                                "/user/code",
                                                "/user/login")
                                .order(1);
        }
}
