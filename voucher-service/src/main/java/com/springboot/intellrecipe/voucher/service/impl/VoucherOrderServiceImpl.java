package com.springboot.intellrecipe.voucher.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.common.dto.MyVoucherDTO;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.VoucherOrderDTO;
import com.springboot.intellrecipe.common.entity.Voucher;
import com.springboot.intellrecipe.common.entity.VoucherOrder;
import com.springboot.intellrecipe.common.utils.RedisIdWorker;
import com.springboot.intellrecipe.common.utils.UserHolder;
import com.springboot.intellrecipe.voucher.mapper.VoucherOrderMapper;
import com.springboot.intellrecipe.voucher.service.SeckillVoucherService;
import com.springboot.intellrecipe.voucher.service.VoucherOrderService;
import com.springboot.intellrecipe.voucher.service.VoucherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.springboot.intellrecipe.api.dto.VoucherReleaseDTO;
import com.springboot.intellrecipe.api.dto.VoucherBriefDTO;
import com.springboot.intellrecipe.api.dto.VoucherPrecheckDTO;

import com.springboot.intellrecipe.api.dto.VoucherUseDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {

    @Resource
    private SeckillVoucherService seckillVoucherService;

    @Resource
    private VoucherService voucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Long purchaseVoucher(Long voucherId) {
        // 1. 查询优惠券
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null) {
            throw new RuntimeException("优惠券不存在！");
        }

        // 2. 秒杀券逻辑
        if (voucher.getType() == 1) {
            return seckillVoucherService.seckillVoucher(voucherId);
        }

        // 3. 普通券逻辑：同步落库，避免 MQ 异常时接口成功但无订单
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        long orderId = redisIdWorker.nextId("order");
        VoucherOrderDTO orderDTO = new VoucherOrderDTO();
        orderDTO.setOrderId(orderId);
        orderDTO.setUserId(userId);
        orderDTO.setVoucherId(voucherId);
        orderDTO.setType(0);

        createVoucherOrder(orderDTO);
        return orderId;
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrderDTO voucherOrderDTO) {
        Long userId = voucherOrderDTO.getUserId();
        Long voucherId = voucherOrderDTO.getVoucherId();
        Integer type = voucherOrderDTO.getType();

        if (userId == null) {
            throw new RuntimeException("订单用户ID为空，请重新登录");
        }

        // 幂等：订单已存在则直接返回
        if (getById(voucherOrderDTO.getOrderId()) != null) {
            return;
        }

        // 1. 扣减库存 (秒杀券)
        boolean success = true;
        if (type == 1) {
            success = seckillVoucherService.deductStock(voucherId);
            if (!success) {
                // 抛出异常以触发MQ的消费失败（进入死信队列进行重试或人工干预）
                throw new RuntimeException("扣减库存失败，数据库操作未生效");
            }
        }

        // 2. 保存订单
        if (success) {
            LocalDateTime now = LocalDateTime.now();
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(voucherOrderDTO.getOrderId());
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setPayType(1);
            voucherOrder.setStatus(1);
            voucherOrder.setCreateTime(now);
            voucherOrder.setUpdateTime(now);

            // 设置过期时间 = 创建时间 + 有效期
            Voucher voucher = voucherService.getById(voucherId);
            if (voucher != null && voucher.getValidityDays() != null && voucher.getValidityDays() > 0) {
                voucherOrder.setExpireTime(now.plusDays(voucher.getValidityDays()));
            }

            save(voucherOrder);
        }
    }

    @Override
    public Result queryMyVouchers() {
        Long userId = UserHolder.getUser().getId();

        // 惰性过期：先将已过期的优惠券状态置为0
        baseMapper.updateExpiredVouchers();

        // 查询并过滤掉已过期的优惠券（status=0），前端不展示
        List<MyVoucherDTO> vouchers = baseMapper.selectMyVouchers(userId);
        List<MyVoucherDTO> activeVouchers = vouchers.stream()
                .filter(v -> v.getStatus() != null && v.getStatus() == 1)
                .collect(Collectors.toList());
        return Result.ok(activeVouchers);
    }

    @Override
    public Result queryUsableByShop(Long userId, Long shopId) {
        // 惰性过期：将已过期的券置为 status=0
        baseMapper.updateExpiredVouchers();
        List<MyVoucherDTO> vouchers = baseMapper.selectMyVouchers(userId);
        LocalDateTime now = LocalDateTime.now();
        List<MyVoucherDTO> usable = vouchers.stream()
                .filter(v -> v.getStatus() != null && (v.getStatus() == 1 || v.getStatus() == 2))
                .filter(v -> v.getShopId() != null && v.getShopId().equals(shopId))
                .filter(v -> v.getExpireTime() == null || v.getExpireTime().isAfter(now))
                .collect(Collectors.toList());
        return Result.ok(usable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal useVoucher(VoucherUseDTO dto) {
        if (dto == null || dto.getUserId() == null || dto.getVoucherOrderId() == null) {
            throw new RuntimeException("核销参数缺失");
        }
        VoucherOrder vo = getById(dto.getVoucherOrderId());
        if (vo == null || !vo.getUserId().equals(dto.getUserId())) {
            throw new RuntimeException("优惠券不存在或不属于当前用户");
        }
        LocalDateTime now = LocalDateTime.now();

        // 已核销：幂等——同订单号重复调用返回原抵扣额；否则视为已被其它订单占用
        if (vo.getStatus() != null && vo.getStatus() == 3) {
            if (dto.getOrderNo() != null && dto.getOrderNo().equals(vo.getUsedOrderNo())) {
                return discountOf(dto.getVoucherOrderId());
            }
            throw new RuntimeException("优惠券已被使用");
        }
        if (vo.getStatus() == null || (vo.getStatus() != 1 && vo.getStatus() != 2)) {
            throw new RuntimeException("优惠券状态不可用");
        }
        if (vo.getExpireTime() != null && !vo.getExpireTime().isAfter(now)) {
            throw new RuntimeException("优惠券已过期");
        }
        if (dto.getOrderNo() == null || dto.getShopId() == null) {
            throw new RuntimeException("缺少关联订单号或商家信息");
        }

        Voucher voucher = voucherService.getById(vo.getVoucherId());
        if (voucher == null) {
            throw new RuntimeException("优惠券模板不存在");
        }
        if (!voucher.getShopId().equals(dto.getShopId())) {
            throw new RuntimeException("该优惠券不适用于本商家订单");
        }
        // 满减门槛校验（actual_value 单位：分）
        if (voucher.getActualValue() != null && voucher.getActualValue() > 0) {
            BigDecimal orderAmount = dto.getOrderAmount() == null ? BigDecimal.ZERO : dto.getOrderAmount();
            if (orderAmount.multiply(BigDecimal.valueOf(100)).compareTo(BigDecimal.valueOf(voucher.getActualValue())) < 0) {
                throw new RuntimeException("订单金额未达到优惠券使用门槛");
            }
        }

        // 原子核销：并发下仅一笔成功（status∈{1,2} 且未被使用）
        boolean updated = update(new LambdaUpdateWrapper<VoucherOrder>()
                .eq(VoucherOrder::getId, vo.getId())
                .eq(VoucherOrder::getUserId, vo.getUserId())
                .in(VoucherOrder::getStatus, 1, 2)
                .isNull(VoucherOrder::getUsedOrderNo)
                .set(VoucherOrder::getStatus, 3)
                .set(VoucherOrder::getUseTime, now)
                .set(VoucherOrder::getUsedOrderNo, dto.getOrderNo()));
        if (!updated) {
            VoucherOrder latest = getById(vo.getId());
            if (latest != null && dto.getOrderNo().equals(latest.getUsedOrderNo())) {
                return discountOf(dto.getVoucherOrderId());
            }
            throw new RuntimeException("优惠券已被使用");
        }
        return discountOf(dto.getVoucherOrderId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseVoucher(VoucherReleaseDTO dto) {
        if (dto == null || dto.getUserId() == null || dto.getVoucherOrderId() == null) {
            throw new RuntimeException("退券参数缺失");
        }
        VoucherOrder vo = getById(dto.getVoucherOrderId());
        if (vo == null || !vo.getUserId().equals(dto.getUserId())) {
            throw new RuntimeException("优惠券不存在或不属于当前用户");
        }
        if (vo.getStatus() == null || vo.getStatus() != 3) {
            // 未核销 / 已退回：幂等放行
            return;
        }
        if (dto.getOrderNo() == null || !dto.getOrderNo().equals(vo.getUsedOrderNo())) {
            throw new RuntimeException("该券未核销到指定订单，不允许退回");
        }
        update(new LambdaUpdateWrapper<VoucherOrder>()
                .eq(VoucherOrder::getId, vo.getId())
                .eq(VoucherOrder::getUserId, vo.getUserId())
                .eq(VoucherOrder::getStatus, 3)
                .set(VoucherOrder::getStatus, 2)
                .set(VoucherOrder::getUseTime, null)
                .set(VoucherOrder::getUsedOrderNo, null));
    }

    /**
     * 计算某张券的抵扣金额（元）：pay_value（分）÷100
     */
    private BigDecimal discountOf(Long voucherOrderId) {
        VoucherOrder vo = getById(voucherOrderId);
        Voucher voucher = vo == null ? null : voucherService.getById(vo.getVoucherId());
        if (voucher == null || voucher.getPayValue() == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(voucher.getPayValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Override
    public VoucherBriefDTO precheck(VoucherPrecheckDTO dto) {
        if (dto == null || dto.getUserId() == null || dto.getVoucherOrderId() == null) {
            throw new RuntimeException("校验参数缺失");
        }
        VoucherOrder vo = getById(dto.getVoucherOrderId());
        if (vo == null || !vo.getUserId().equals(dto.getUserId())) {
            throw new RuntimeException("优惠券不存在或不属于当前用户");
        }
        if (vo.getStatus() == null || (vo.getStatus() != 1 && vo.getStatus() != 2)) {
            throw new RuntimeException("优惠券不可用");
        }
        if (vo.getExpireTime() != null && !vo.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("优惠券已过期");
        }
        Voucher voucher = voucherService.getById(vo.getVoucherId());
        if (voucher == null) {
            throw new RuntimeException("优惠券模板不存在");
        }
        VoucherBriefDTO brief = new VoucherBriefDTO();
        brief.setVoucherOrderId(vo.getId());
        brief.setVoucherId(voucher.getId());
        brief.setShopId(voucher.getShopId());
        brief.setTitle(voucher.getTitle());
        brief.setPayValue(voucher.getPayValue());
        brief.setActualValue(voucher.getActualValue());
        brief.setType(voucher.getType());
        brief.setStatus(vo.getStatus());
        brief.setExpireTime(vo.getExpireTime());
        return brief;
    }
}