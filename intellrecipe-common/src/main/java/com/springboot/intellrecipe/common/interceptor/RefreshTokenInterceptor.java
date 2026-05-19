package com.springboot.intellrecipe.common.interceptor;

import static com.springboot.intellrecipe.common.utils.RedisConstants.LOGIN_USER_KEY;
import static com.springboot.intellrecipe.common.utils.RedisConstants.LOGIN_USER_TTL;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.springboot.intellrecipe.common.dto.UserDTO;
import com.springboot.intellrecipe.common.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }

        // 2.基于TOKEN获取redis中的用户
        String key  = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        // 3.判断用户是否存在
        if (userMap.isEmpty()) {
            return true;
        }

        // 4.将查询到的Hash数据转为UserDTO对象（Hash 中数值均为 String，需显式转换 id）
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        Object idVal = userMap.get("id");
        if (idVal != null && StrUtil.isNotBlank(idVal.toString())) {
            userDTO.setId(Long.valueOf(idVal.toString()));
        }

        // 5.存在，保存用户信息到 ThreadLocal
        UserHolder.saveUser(userDTO);

        // 6.刷新token有效期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 7.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
