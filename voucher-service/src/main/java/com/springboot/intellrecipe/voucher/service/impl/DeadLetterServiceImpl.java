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

@Slf4j
@Service
public class DeadLetterServiceImpl extends ServiceImpl<DeadLetterMapper, DeadLetter> implements DeadLetterService {

    @Resource
    private VoucherOrderService voucherOrderService;

    @Override
    public boolean retryDeadLetter(Long id) {
        DeadLetter deadLetter = getById(id);
        if (deadLetter == null || deadLetter.getStatus() == 1) {
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
}