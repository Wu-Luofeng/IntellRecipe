package com.springboot.intellrecipe;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import com.springboot.intellrecipe.common.dto.UserDTO;
import com.springboot.intellrecipe.common.entity.User;
import com.springboot.intellrecipe.common.utils.RedisConstants;
import com.springboot.intellrecipe.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SpringBootTest(classes = UserApplication.class)
public class UserTokenTest {

    @Resource
    private UserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 批量生成 1000 个用户并缓存 Token
     * 生成的 token 文件位于项目根目录下的 tokens.txt
     */
    @Test
    void createTokens() throws IOException {
        // 1. 准备 1000 个用户
        List<User> users = userService.list();
        
        // 如果用户不足 1000，补齐用户
        int count = 1000;
        if (users.size() < count) {
            List<User> newUsers = new ArrayList<>();
            for (int i = users.size(); i < count; i++) {
                User user = new User();
                user.setPhone("1380000" + String.format("%04d", i));
                user.setNickname("User_" + i);
                newUsers.add(user);
            }
            if(!newUsers.isEmpty()){
                userService.saveBatch(newUsers);
                users.addAll(newUsers);
            }
        }
        
        // 截取前 1000 个用户
        users = users.subList(0, count);

        // 2. 为每个用户生成 Token 并写入 Redis
        File tokenFile = new File("tokens.txt");
        if (tokenFile.exists()) {
            tokenFile.delete();
        }

        List<String> tokens = new ArrayList<>();
        
        for (User user : users) {
             // 生成 token
            String token = UUID.randomUUID().toString(true);

            // 转换为 DTO
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

            // 存入 Redis
            String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            // 设置有效期 (测试专用：30天)
            stringRedisTemplate.expire(tokenKey, 30L, TimeUnit.DAYS);
            
            tokens.add(token);
        }

        // 3. 将 token 写入文件 (每行一个)
        FileUtil.writeLines(tokens, tokenFile, "UTF-8");

        System.out.println("成功生成 1000 个 Token，文件路径: " + tokenFile.getAbsolutePath());
    }
}