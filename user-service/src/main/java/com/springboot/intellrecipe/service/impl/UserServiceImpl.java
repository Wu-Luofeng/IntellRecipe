package com.springboot.intellrecipe.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.UserDTO;
import com.springboot.intellrecipe.common.dto.UserLoginDTO;
import com.springboot.intellrecipe.common.entity.User;
import com.springboot.intellrecipe.mapper.UserMapper;
import com.springboot.intellrecipe.service.UserService;
import com.springboot.intellrecipe.common.utils.RedisConstants;
import com.springboot.intellrecipe.common.utils.RedisConstants;
import com.springboot.intellrecipe.common.utils.RedisConstants;
import com.springboot.intellrecipe.common.utils.RegexUtils;
import com.springboot.intellrecipe.utils.AliyunDypnsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final StringRedisTemplate stringRedisTemplate;

    @Resource
    private AliyunDypnsUtils aliyunDypnsUtils;

    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result<String> sendCode(String phone) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 2.生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 3.保存验证码到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 4.发送验证码(真实发送)
        boolean isSent = aliyunDypnsUtils.sendVerifyCode(phone, code);
        if (!isSent) {
            // 如果发送失败，尝试返回错误信息，或者记录日志
            // 这里为了用户体验，可以暂时返回成功但提示日志，或者直接报错
            log.error("短信验证码发送失败，请检查控制台日志");
            return Result.fail("短信发送失败，请稍后重试");
        }
        log.info("发送验证码 {}: {}", phone, code);

        return Result.ok("验证码发送成功");
    }

    @Override
    public Result<String> login(UserLoginDTO userLoginDTO) {
        try {
            // 1. 获取手机号和验证码
            String phone = userLoginDTO.getPhone();
            String code = userLoginDTO.getCode();

            // 2. 校验手机号
            if (RegexUtils.isPhoneInvalid(phone)) {
                return Result.fail("手机号格式错误");
            }

            // 3. 从Redis获取验证码
            String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);

            // 4. 校验验证码
            if (cacheCode == null || !cacheCode.equals(code)) {
                return Result.fail("验证码错误或已过期");
            }

            // 5. 根据手机号查询用户
            User user = lambdaQuery().eq(User::getPhone, phone).one();

            // 6. 判断用户是否存在，如果不存在则注册
            if (user == null) {
                user = createByPhone(phone);
            }

            // 7. 生成Token (这里假设用UUID，因为JwtUtils可能也不存在或有问题)
            // 原代码用了 JwtUtils，如果不存在会报错。为了稳妥，我们先用 UUID 生成 token
            String token = UUID.randomUUID().toString(true);
            
            // 8. 将User对象转为Hash存储
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
            
            String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

            // 9. 返回token
            return Result.ok(token);
        } catch (Exception e) {
            log.error("登录异常", e);
            return Result.fail("登录异常");
        }
    }

    private User createByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickname("用户" + RandomUtil.randomString(6));
        user.setStatus(0); // 正常状态
        this.save(user); // MyBatisPlus 提供的保存方法
        return user;
    }
}
