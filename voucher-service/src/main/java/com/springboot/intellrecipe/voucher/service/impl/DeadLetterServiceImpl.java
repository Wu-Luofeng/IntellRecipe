package com.springboot.intellrecipe.voucher.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.common.dto.VoucherOrderDTO;
import com.springboot.intellrecipe.voucher.entity.DeadLetter;
import com.springboot.intellrecipe.voucher.mapper.DeadLetterMapper;
import com.springboot.intellrecipe.voucher.service.DeadLetterService;
import com.springboot.intellrecipe.voucher.service.VoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Slf4j
@Service
public class DeadLetterServiceImpl extends ServiceImpl<DeadLetterMapper, DeadLetter> implements DeadLetterService {

    @Resource
    private VoucherOrderService voucherOrderService;

    @Resource
    private com.springboot.intellrecipe.voucher.service.SeckillVoucherService seckillVoucherService;

    @Override
    public boolean retryDeadLetter(Long id) {
        DeadLetter deadLetter = getById(id);
        if (deadLetter == null || deadLetter.getStatus() == null
                || deadLetter.getStatus() == 1 || deadLetter.getStatus() == 3) {
            log.warn("死信不存在或已处理完毕: id={}", id);
            return false;
        }

        try {
            // 将 JSON 字符串反序列化回对象
            VoucherOrderDTO dto = JSONUtil.toBean(deadLetter.getContent(), VoucherOrderDTO.class);

            log.info("开始执行死信人工补偿: orderId={}, userId={}", dto.getOrderId(), dto.getUserId());
            voucherOrderService.createVoucherOrder(dto);

            // 补偿成功，更新状态
            deadLetter.setStatus(1); // 1-已处理
            updateById(deadLetter);
            return true;

        } catch (DataIntegrityViolationException e) {
            // 幂等：说明订单之前其实已经成功创建了，直接标为已处理
            log.info("死信补偿触发幂等（订单已存在），标记为处理成功: id={}", id);
            deadLetter.setStatus(1);
            updateById(deadLetter);
            return true;

        } catch (Exception e) {
            log.error("死信人工补偿失败: id={}", id, e);
            // 补偿失败，增加重试次数，状态改为 2-处理失败
            deadLetter.setRetryCount(deadLetter.getRetryCount() + 1);
            deadLetter.setStatus(2);
            deadLetter.setReason(e.getMessage());
            updateById(deadLetter);
            return false;
        }
    }
    @Override
    public boolean abandonDeadLetter(Long id) {
        DeadLetter deadLetter = getById(id);
        if (deadLetter == null) {
            log.warn("死信不存在，无法放弃: id={}", id);
            return false;
        }
        Integer st = deadLetter.getStatus();
        if (st != null && (st == 1 || st == 3)) {
            log.warn("死信已是终态（已处理/已放弃），拒绝重复放弃: id={}, status={}", id, st);
            return false;
        }
        // CAS 抢占放弃权：0/2 -> 3。先锁状态再补偿，防止定时任务与人工同时触发双份补偿
        boolean locked = update()
                .set("status", 3)
                .set("update_time", LocalDateTime.now())
                .eq("id", id)
                .in("status", 0, 2)
                .update();
        if (!locked) {
            log.warn("CAS 抢占失败（状态已被并发修改）: id={}", id);
            return false;
        }
        // 补偿：仅秒杀消息有 Redis 资产需要归还（普通券无 Redis 资产）
        try {
            VoucherOrderDTO dto = JSONUtil.toBean(deadLetter.getContent(), VoucherOrderDTO.class);
            if (Integer.valueOf(1).equals(dto.getType())) {
                seckillVoucherService.rollbackSeckill(dto.getVoucherId(), dto.getUserId());
            }
            log.info("[DeadLetterAbandon] 死信放弃并补偿完成: id={}, orderId={}, userId={}, voucherId={}, type={}",
                    id, dto.getOrderId(), dto.getUserId(), dto.getVoucherId(), dto.getType());
            return true;
        } catch (Exception e) {
            // Redis 补偿执行失败：状态退回 2-处理失败，等待下次放弃重试（幂等由 CAS+异常回退保证）
            update().set("status", 2).set("update_time", LocalDateTime.now()).eq("id", id).update();
            log.error("[DeadLetterAbandon] 补偿执行失败，状态回退待重试: id={}", id, e);
            return false;
        }
    }

    @Override
    public int abandonTimeoutDeadLetters(int hours) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(hours);
        List<DeadLetter> stale = list(new LambdaQueryWrapper<DeadLetter>()
                .in(DeadLetter::getStatus, 0, 2)
                .lt(DeadLetter::getCreateTime, threshold));
        int ok = 0;
        for (DeadLetter dl : stale) {
            if (abandonDeadLetter(dl.getId())) {
                ok++;
            }
        }
        if (ok > 0) {
            log.info("[DeadLetterSweep] 超时死信自动放弃并补偿 {} 条（扫描 {} 条）", ok, stale.size());
        }
        return ok;
    }
}