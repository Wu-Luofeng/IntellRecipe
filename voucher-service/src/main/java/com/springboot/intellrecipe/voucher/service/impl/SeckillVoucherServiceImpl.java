package com.springboot.intellrecipe.voucher.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.common.dto.VoucherOrderDTO;
import com.springboot.intellrecipe.common.entity.SeckillVoucher;
import com.springboot.intellrecipe.common.utils.RedisConstants;
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
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("lua/seckill_rollback.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
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

        // 2. 校验登录用户
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }

        // 3. 将数据库库存同步到 Redis（首次秒杀前初始化）
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey))) {
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(voucher.getStock()));
        }

        // 4. 提前生成订单ID
        long orderId = redisIdWorker.nextId("order");

        // 5. 执行 Lua 脚本进行资格校验并同时将事件写入 Redis Stream (Outbox)
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );

        // 6. 判断结果
        int r = result.intValue();
        if (r != 0) {
            throw new RuntimeException(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 7. 校验通过并已写入 Redis Stream，由 Dispatcher 转发至 MQ 后落库
        // 后续由专门的 Dispatcher 组件从 Stream 监听，确认发往 MQ，保证可靠性。
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

    @Override
    public boolean rollbackSeckill(Long voucherId, Long userId) {
        Long r = stringRedisTemplate.execute(ROLLBACK_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString());
        org.slf4j.LoggerFactory.getLogger(SeckillVoucherServiceImpl.class)
                .info("[SeckillRollback] 放弃补偿完成: voucherId={}, userId={}, result={}", voucherId, userId, r);
        return r != null && r == 1;
    }

    @Override
    public void restoreStock(Long voucherId) {
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        // key 不存在说明 Redis 库存已整体丢失（下次秒杀会从 DB 重新初始化，且 DB
        // 侧已随事务回滚，值是准的），此时无需补偿，避免 increment 造出错误的 "1"
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey))) {
            return;
        }
        stringRedisTemplate.opsForValue().increment(stockKey);
    }
}
