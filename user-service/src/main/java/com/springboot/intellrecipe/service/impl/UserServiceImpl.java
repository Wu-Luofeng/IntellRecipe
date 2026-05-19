package com.springboot.intellrecipe.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String code;

        // 2. 开发模式（AccessKey 未配置）：本地生成验证码，直接返回，无需真实短信
        if (!aliyunDypnsUtils.isConfigured()) {
            code = RandomUtil.randomNumbers(6);
            stringRedisTemplate.opsForValue().set(
                    RedisConstants.LOGIN_CODE_KEY + phone, code,
                    RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
            log.warn("[DEV MODE] AccessKey 未配置，验证码不会发送至手机。phone={}, code={}", phone, code);
            return Result.ok("[开发模式] 验证码：" + code);
        }

        // 3. 生产模式：调用 DYPNS SendSmsVerifyCode，验证码由阿里云生成并下发
        code = aliyunDypnsUtils.sendVerifyCode(phone);
        if (code == null) {
            log.error("短信验证码发送失败，请检查 AccessKey、赠送签名及赠送模板配置。phone={}", phone);
            return Result.fail("短信发送失败，请稍后重试");
        }

        // 4. 保存阿里云返回的验证码到 Redis
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone, code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("短信验证码已发送。phone={}", phone);

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
            if (user.getId() == null) {
                user = lambdaQuery().eq(User::getPhone, phone).one();
            }
            if (user == null || user.getId() == null) {
                log.error("用户注册或查询失败，phone={}", phone);
                return Result.fail("用户创建失败，请稍后重试");
            }

            // 7. 登录成功后删除验证码，防止重复使用
            stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + phone);

            // 8. 生成 Token 并写入 Redis（显式写入 id，避免 Hash 反序列化丢失）
            String token = UUID.randomUUID().toString(true);
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId().toString());
            userMap.put("phone", user.getPhone());
            if (userDTO.getNickname() != null) {
                userMap.put("nickname", userDTO.getNickname());
            }
            if (userDTO.getIcon() != null) {
                userMap.put("icon", userDTO.getIcon());
            }

            String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

            log.info("用户登录成功: userId={}, phone={}", user.getId(), phone);
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
        user.setStatus(0);
        boolean saved = this.save(user);
        if (!saved) {
            throw new RuntimeException("用户保存失败");
        }
        log.info("新用户注册成功: userId={}, phone={}", user.getId(), phone);
        return user;
    }
}
