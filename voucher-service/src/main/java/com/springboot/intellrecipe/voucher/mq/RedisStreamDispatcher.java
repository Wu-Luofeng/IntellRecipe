package com.springboot.intellrecipe.voucher.mq;

import com.springboot.intellrecipe.common.dto.VoucherOrderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class RedisStreamDispatcher implements InitializingBean, DisposableBean {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    private static final String STREAM_KEY = "stream.orders";
    private static final String GROUP_NAME = "group1";
    private static final String CONSUMER_NAME = "consumer1";

    private final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private volatile boolean isRunning = true;

    @Override
    public void afterPropertiesSet() {
        // 1. 尝试初始化 Stream 和 Consumer Group
        try {
            stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME);
            log.info("成功创建 Redis Stream 和 消费组");
        } catch (Exception e) {
            log.info("Redis Stream 和 消费组可能已存在，跳过创建。");
        }

        // 2. 启动异步线程，不断监听并转发消息
        POOL.submit(() -> {
            while (isRunning) {
                try {
                    // XREADGROUP GROUP group1 consumer1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
                    );

                    // 判断是否有新消息
                    if (records == null || records.isEmpty()) {
                        // 没有新消息，尝试处理 Pending List 中的消息（兜底）
                        handlePendingList();
                        continue;
                    }

                    // 处理新消息并发送到 RabbitMQ
                    MapRecord<String, Object, Object> record = records.get(0);
                    processAndForward(record);

                } catch (Exception e) {
                    log.error("Redis Stream 消息分发异常，可能 Stream 不存在或网络波动: {}", e.getMessage());
                    try { Thread.sleep(2000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                }
            }
        });
    }

    private void handlePendingList() {
        try {
            // XREADGROUP GROUP group1 consumer1 COUNT 1 STREAMS stream.orders 0
            List<MapRecord<String, Object, Object>> pendingRecords = stringRedisTemplate.opsForStream().read(
                    Consumer.from(GROUP_NAME, CONSUMER_NAME),
                    StreamReadOptions.empty().count(1),
                    StreamOffset.create(STREAM_KEY, ReadOffset.from("0"))
            );

            if (pendingRecords == null || pendingRecords.isEmpty()) {
                return; // 没有挂起的消息
            }

            // 如果有未确认的消息，说明上一轮发给 MQ 失败了，或者服务宕机没来得及 ACK，进行重发
            MapRecord<String, Object, Object> record = pendingRecords.get(0);
            log.warn("发现 Pending List 中有未确认的消息，准备重发，Stream ID: {}", record.getId());
            processAndForward(record);

        } catch (Exception e) {
            log.error("处理 Pending List 异常: {}", e.getMessage());
        }
    }

    private void processAndForward(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        String recordId = record.getId().getValue(); // 这是 Redis Stream 自动生成的 ID

        // 解析 Lua 脚本写入的字段
        String userIdStr = (String) value.get("userId");
        String voucherIdStr = (String) value.get("voucherId");
        String orderIdStr = (String) value.get("id");
        String typeStr = (String) value.get("type");

        VoucherOrderDTO dto = new VoucherOrderDTO();
        dto.setUserId(Long.valueOf(userIdStr));
        dto.setVoucherId(Long.valueOf(voucherIdStr));
        dto.setOrderId(Long.valueOf(orderIdStr));
        dto.setType(Integer.valueOf(typeStr));

        // 构造 CorrelationData，将 Redis Stream 的 ID 藏进去发给 MQ
        CorrelationData correlationData = new CorrelationData(recordId);

        log.info("转发 Stream 消息到 RabbitMQ，订单: {}, Stream ID: {}", orderIdStr, recordId);
        // 发送给 MQ
        rabbitTemplate.convertAndSend("voucher.direct", "seckill", dto, correlationData);
    }

    @Override
    public void destroy() {
        isRunning = false;
        POOL.shutdown();
    }
}
