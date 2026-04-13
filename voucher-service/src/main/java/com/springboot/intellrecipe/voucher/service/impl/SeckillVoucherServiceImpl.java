package com.springboot.intellrecipe.voucher.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.common.dto.VoucherOrderDTO;
import com.springboot.intellrecipe.common.entity.SeckillVoucher;
import com.springboot.intellrecipe.common.utils.RedisIdWorker;
import com.springboot.intellrecipe.common.utils.UserHolder;
import com.springboot.intellrecipe.voucher.mapper.SeckillVoucherMapper;
import com.springboot.intellrecipe.voucher.service.SeckillVoucherService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements SeckillVoucherService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Long seckillVoucher(Long voucherId) {
        // 1.查询秒杀信息
        SeckillVoucher voucher = getById(voucherId);
        if (voucher == null) {
            throw new RuntimeException("秒杀券不存在!");
        }
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("秒杀尚未开始！");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("秒杀已经结束！");
        }

        // 2.执行 Lua 脚本进行资格校验
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );

        // 3. 判断结果
        int r = result.intValue();
        if (r != 0) {
            throw new RuntimeException(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 4. 发送消息到 RabbitMQ
        long orderId = redisIdWorker.nextId("order");

        VoucherOrderDTO orderDTO = new VoucherOrderDTO();
        orderDTO.setUserId(userId);
        orderDTO.setVoucherId(voucherId);
        orderDTO.setOrderId(orderId);
        orderDTO.setType(1); // 1 代表秒杀券

        rabbitTemplate.convertAndSend("voucher.direct", "seckill", orderDTO);

        return orderId;
    }

    @Override
    @Transactional
    public boolean deductStock(Long voucherId) {
        return update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
    }
}
