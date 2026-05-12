package com.springboot.intellrecipe.common.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.springboot.intellrecipe.common.dto.RedisData;
import com.springboot.intellrecipe.common.dto.ScrollResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
            10, 
            10, 
            0L, 
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(100), // 使用有界队列防止 OOM
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy() // 拒绝策略
    );

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    public <R> R queryWithLogicalExpire(
            String key, String lockKey, Class<R> type, Supplier<R> dbFallback, Long time, TimeUnit unit) {
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3.不存在，直接返回null或者查询数据库并建立缓存
            // 实际上逻辑过期通常假设热点key一直在缓存里
            R r = dbFallback.get();
            if (r != null && !isEmptyScrollResult(r)) {
                this.setWithLogicalExpire(key, r, time, unit);
            }
            return r;
        }
        // 3.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.未过期；若缓存的是「空列表」ScrollResult（常见于先访问页面后导数），丢弃并回源
            if (isEmptyScrollResult(r)) {
                stringRedisTemplate.delete(key);
                R fresh = dbFallback.get();
                if (fresh != null && !isEmptyScrollResult(fresh)) {
                    this.setWithLogicalExpire(key, fresh, time, unit);
                }
                return fresh != null ? fresh : r;
            }
            return r;
        }
        // 6.已过期，需要缓存重建
        // 6.1.获取互斥锁
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取锁成功
        if (isLock){
            // 6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R newR = dbFallback.get();
                    if (newR != null && !isEmptyScrollResult(newR)) {
                        this.setWithLogicalExpire(key, newR, time, unit);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4.返回过期的商铺信息
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /** 空列表不写缓存、命中时视为脏数据需回源，避免「先空库访问」挡 30 分钟无数据。 */
    private static boolean isEmptyScrollResult(Object o) {
        if (!(o instanceof ScrollResult)) {
            return false;
        }
        ScrollResult sr = (ScrollResult) o;
        return sr.getList() == null || sr.getList().isEmpty();
    }
}
