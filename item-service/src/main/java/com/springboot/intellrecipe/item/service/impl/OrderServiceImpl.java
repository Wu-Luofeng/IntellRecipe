package com.springboot.intellrecipe.item.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.springboot.intellrecipe.api.client.VoucherClient;
import com.springboot.intellrecipe.api.dto.VoucherBriefDTO;
import com.springboot.intellrecipe.api.dto.VoucherPrecheckDTO;
import com.springboot.intellrecipe.api.dto.VoucherReleaseDTO;
import com.springboot.intellrecipe.api.dto.VoucherUseDTO;
import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.entity.Cart;
import com.springboot.intellrecipe.common.entity.Merchant;
import com.springboot.intellrecipe.common.entity.Product;
import com.springboot.intellrecipe.item.config.OrderRabbitConfig;
import com.springboot.intellrecipe.item.dto.CreateOrderDTO;
import com.springboot.intellrecipe.item.dto.OrderDetailDTO;
import com.springboot.intellrecipe.item.entity.TradeOrder;
import com.springboot.intellrecipe.item.entity.TradeOrderItem;
import com.springboot.intellrecipe.item.entity.TradeOrderStatusLog;
import com.springboot.intellrecipe.item.mapper.TradeOrderItemMapper;
import com.springboot.intellrecipe.item.mapper.TradeOrderMapper;
import com.springboot.intellrecipe.item.mapper.TradeOrderStatusLogMapper;
import com.springboot.intellrecipe.item.service.CartService;
import com.springboot.intellrecipe.item.service.MerchantService;
import com.springboot.intellrecipe.item.service.OrderService;
import com.springboot.intellrecipe.item.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl extends ServiceImpl<TradeOrderMapper, TradeOrder> implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Resource
    private CartService cartService;
    @Resource
    private ProductService productService;
    @Resource
    private MerchantService merchantService;
    @Resource
    private TradeOrderItemMapper tradeOrderItemMapper;
    @Resource
    private TradeOrderStatusLogMapper statusLogMapper;
    @Resource
    private VoucherClient voucherClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public List<String> createOrders(Long userId, CreateOrderDTO dto) {
        if (dto == null
                || StrUtil.hasBlank(dto.getReceiverName(), dto.getReceiverPhone(), dto.getReceiverAddress())) {
            throw new RuntimeException("请填写完整的收货人/电话/地址");
        }
        // D1 幂等：同一结算会话(clientToken)已存在“处理中/待支付”订单 → 直接返回原单，防止重复下单
        if (StrUtil.isNotBlank(dto.getClientToken())) {
            TradeOrder existed = getOne(new LambdaQueryWrapper<TradeOrder>()
                    .eq(TradeOrder::getUserId, userId)
                    .eq(TradeOrder::getClientToken, dto.getClientToken())
                    .in(TradeOrder::getStatus, TradeOrder.STATUS_PROCESSING, TradeOrder.STATUS_PENDING)
                    .orderByDesc(TradeOrder::getCreateTime)
                    .last("limit 1"));
            if (existed != null) {
                log.info("命中结算幂等键，返回原订单。userId={}, clientToken={}, orderNo={}",
                        userId, dto.getClientToken(), existed.getOrderNo());
                return Collections.singletonList(existed.getOrderNo());
            }
        }
        List<Cart> selected = resolveSelectedCarts(userId, dto.getCartIds());
        if (selected.isEmpty()) {
            throw new RuntimeException("没有可结算的商品，请先在购物车勾选商品");
        }
        // 有券：precheck 校验并取得券所属商家，且必须在本单商品中存在该商家
        Long voucherShopId = null;
        Long voucherOrderId = dto.getVoucherOrderId();
        if (voucherOrderId != null) {
            VoucherPrecheckDTO pc = new VoucherPrecheckDTO();
            pc.setUserId(userId);
            pc.setVoucherOrderId(voucherOrderId);
            Result<VoucherBriefDTO> pcr = voucherClient.precheck(pc);
            if (pcr == null || !Boolean.TRUE.equals(pcr.getSuccess()) || pcr.getData() == null) {
                throw new RuntimeException(pcr == null ? "优惠券校验失败" : pcr.getErrorMsg());
            }
            voucherShopId = pcr.getData().getShopId();
            final Long fs = voucherShopId;
            boolean shopInCart = selected.stream().anyMatch(c -> c.getMerchantId() != null
                    && c.getMerchantId().equals(fs));
            if (!shopInCart) {
                throw new RuntimeException("所选优惠券不适用于本次结算的商品");
            }
        }

        // 整单模型：一次结算只生成一个订单（明细保留各自商家）
        final Long finalShopId = voucherShopId;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            final String orderNo = genOrderNo();
            tx.executeWithoutResult(s -> lockAndWriteOrder(userId, selected, dto, orderNo,
                    voucherOrderId, finalShopId));
            sendOrderCreate(orderNo);
            return Collections.singletonList(orderNo);
        } catch (DuplicateKeyException e) {
            // 并发重复提交或订单号极小概率冲突（唯一索引兜底）
            if (StrUtil.isNotBlank(dto.getClientToken())) {
                TradeOrder dup = getOne(new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getUserId, userId)
                        .eq(TradeOrder::getClientToken, dto.getClientToken())
                        .in(TradeOrder::getStatus, TradeOrder.STATUS_PROCESSING, TradeOrder.STATUS_PENDING)
                        .orderByDesc(TradeOrder::getCreateTime)
                        .last("limit 1"));
                if (dup != null) {
                    log.warn("并发下命中幂等键，返回已受理订单。orderNo={}", dup.getOrderNo());
                    if (dup.getStatus() != null && dup.getStatus() == TradeOrder.STATUS_PROCESSING) {
                        sendOrderCreate(dup.getOrderNo());
                    }
                    return Collections.singletonList(dup.getOrderNo());
                }
            }
            // D4：订单号冲突时重生成再试一次
            final String retryNo = genOrderNo();
            log.warn("订单号唯一冲突，重试生成。retryNo={}", retryNo);
            tx.executeWithoutResult(s -> lockAndWriteOrder(userId, selected, dto, retryNo,
                    voucherOrderId, finalShopId));
            sendOrderCreate(retryNo);
            return Collections.singletonList(retryNo);
        }
    }

    private void sendOrderCreate(String orderNo) {
        try {
            rabbitTemplate.convertAndSend(OrderRabbitConfig.ORDER_EXCHANGE,
                    OrderRabbitConfig.ORDER_ROUTING_KEY, orderNo);
        } catch (Exception e) {
            log.error("下单消息投递异常，将交由定时补偿。orderNo={}", orderNo, e);
        }
    }

    /** 受理事务：写一笔“整单”订单(status=处理中)与快照明细，金额按现价锁定 */
    private void lockAndWriteOrder(Long userId, List<Cart> carts, CreateOrderDTO dto,
                                   String orderNo, Long voucherOrderId, Long voucherMerchantId) {
        Map<Long, Product> productMap = productService
                .listByIds(carts.stream().map(Cart::getProductId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        List<TradeOrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Cart cart : carts) {
            Product product = productMap.get(cart.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在：" + cart.getProductName());
            }
            if (product.getStatus() == null || product.getStatus() != 1) {
                throw new RuntimeException("商品已下架：" + product.getName());
            }
            BigDecimal price = product.getPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = price.multiply(new BigDecimal(cart.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            total = total.add(subtotal);
            items.add(new TradeOrderItem().setMerchantId(cart.getMerchantId())
                    .setProductId(product.getId()).setProductName(product.getName())
                    .setProductImage(product.getImage()).setPrice(price)
                    .setQuantity(cart.getQuantity()).setSubtotal(subtotal));
        }
        total = total.setScale(2, RoundingMode.HALF_UP);

        TradeOrder order = new TradeOrder()
                .setOrderNo(orderNo).setUserId(userId)
                .setMerchantId(null).setMerchantName(null)
                .setVoucherMerchantId(voucherMerchantId)
                .setTotalAmount(total)
                .setDiscountAmount(BigDecimal.ZERO)
                .setPayAmount(total)
                .setVoucherOrderId(voucherOrderId)
                .setReceiverName(dto.getReceiverName())
                .setReceiverPhone(dto.getReceiverPhone())
                .setReceiverAddress(dto.getReceiverAddress())
                .setRemark(dto.getRemark())
                .setClientToken(dto.getClientToken())
                .setStatus(TradeOrder.STATUS_PROCESSING);
        save(order);
        for (TradeOrderItem item : items) {
            item.setOrderNo(orderNo);
            tradeOrderItemMapper.insert(item);
        }
        statusLogMapper.insert(logOf(orderNo, null, TradeOrder.STATUS_PROCESSING,
                userId.toString(), "下单受理（等待异步处理）"));
    }

    /**
     * 异步下单执行体（MQ 消费者 / 定时补偿共用）：
     * 幂等：仅处理 status=处理中 的订单；核销优惠券、推进为“待支付”、清理购物车。
     */
    @Override
    public void processOrderCreate(String orderNo) {
        if (orderNo == null) {
            return;
        }
        TradeOrder order = getOne(new LambdaQueryWrapper<TradeOrder>().eq(TradeOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new RuntimeException("订单不存在：" + orderNo);
        }
        if (order.getStatus() == null || order.getStatus() != TradeOrder.STATUS_PROCESSING) {
            return; // 幂等：非“处理中”说明已处理/已失败
        }
        final boolean[] voucherUsed = {false};
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.executeWithoutResult(s -> {
                TradeOrder cur = getOne(new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getOrderNo, orderNo));
                if (cur == null || cur.getStatus() == null
                        || cur.getStatus() != TradeOrder.STATUS_PROCESSING) {
                    return; // 并发下已被其它消息处理
                }
                // 1. 优惠券核销（voucher-service，幂等）——多商家整单时，按“券所属商家”的商品小计校验门槛
                BigDecimal discount = BigDecimal.ZERO;
                if (cur.getVoucherOrderId() != null) {
                    VoucherUseDTO use = new VoucherUseDTO();
                    use.setUserId(cur.getUserId());
                    use.setVoucherOrderId(cur.getVoucherOrderId());
                    use.setShopId(cur.getVoucherMerchantId() != null
                            ? cur.getVoucherMerchantId() : cur.getMerchantId());
                    use.setOrderNo(orderNo);
                    BigDecimal baseAmount = cur.getTotalAmount();
                    if (cur.getVoucherMerchantId() != null) {
                        baseAmount = tradeOrderItemMapper.selectList(
                                        new LambdaQueryWrapper<TradeOrderItem>()
                                                .eq(TradeOrderItem::getOrderNo, orderNo)
                                                .eq(TradeOrderItem::getMerchantId, cur.getVoucherMerchantId()))
                                .stream().map(TradeOrderItem::getSubtotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                    use.setOrderAmount(baseAmount);
                    Result<BigDecimal> resp = voucherClient.useVoucher(use);
                    if (resp == null || !Boolean.TRUE.equals(resp.getSuccess())) {
                        throw new RuntimeException(resp == null ? "核销优惠券失败" : resp.getErrorMsg());
                    }
                    discount = resp.getData() == null ? BigDecimal.ZERO
                            : resp.getData().setScale(2, RoundingMode.HALF_UP);
                    voucherUsed[0] = true;
                }
                if (discount.compareTo(cur.getTotalAmount()) > 0) {
                    discount = cur.getTotalAmount();
                }
                BigDecimal pay = cur.getTotalAmount().subtract(discount).setScale(2, RoundingMode.HALF_UP);

                // 2. 推进为“待支付”，回填实付金额
                update(new LambdaUpdateWrapper<TradeOrder>()
                        .eq(TradeOrder::getOrderNo, orderNo)
                        .eq(TradeOrder::getStatus, TradeOrder.STATUS_PROCESSING)
                        .set(TradeOrder::getStatus, TradeOrder.STATUS_PENDING)
                        .set(TradeOrder::getDiscountAmount, discount)
                        .set(TradeOrder::getPayAmount, pay)
                        .set(TradeOrder::getFailReason, null));
                statusLogMapper.insert(logOf(orderNo, TradeOrder.STATUS_PROCESSING,
                        TradeOrder.STATUS_PENDING, String.valueOf(cur.getUserId()), "异步下单完成，待支付"));

                // 3. 清理本单已购商品对应的购物车项（幂等：按 productId 删除）
                List<Long> productIds = tradeOrderItemMapper.selectList(
                                new LambdaQueryWrapper<TradeOrderItem>().eq(TradeOrderItem::getOrderNo, orderNo))
                        .stream().map(TradeOrderItem::getProductId).distinct().collect(Collectors.toList());
                if (!productIds.isEmpty()) {
                    cartService.remove(new LambdaQueryWrapper<Cart>()
                            .eq(Cart::getUserId, cur.getUserId())
                            .in(Cart::getProductId, productIds));
                }
            });
        } catch (Exception e) {
            log.error("异步下单失败。orderNo={}, reason={}", orderNo, e.getMessage(), e);
            // 券已核销而本地下单失败 → 补偿退券（最终一致）
            if (voucherUsed[0]) {
                try {
                    TradeOrder cur = getOne(new LambdaQueryWrapper<TradeOrder>()
                            .eq(TradeOrder::getOrderNo, orderNo));
                    VoucherReleaseDTO release = new VoucherReleaseDTO();
                    release.setUserId(cur == null ? null : cur.getUserId());
                    release.setVoucherOrderId(cur == null ? null : cur.getVoucherOrderId());
                    release.setOrderNo(orderNo);
                    voucherClient.releaseVoucher(release);
                } catch (Exception ex) {
                    log.error("补偿退券失败，需人工处理。orderNo={}", orderNo, ex);
                }
            }
            if (!isTransient(e)) {
                markOrderFail(orderNo, e.getMessage());
            }
            throw e;
        }
    }

    /** 将处理中的订单标记为失败（终态，用户可重新结算） */
    private void markOrderFail(String orderNo, String reason) {
        boolean updated = update(new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, orderNo)
                .eq(TradeOrder::getStatus, TradeOrder.STATUS_PROCESSING)
                .set(TradeOrder::getStatus, TradeOrder.STATUS_FAIL)
                .set(TradeOrder::getFailReason, reason == null ? "下单失败" : reason));
        if (updated) {
            TradeOrder cur = getOne(new LambdaQueryWrapper<TradeOrder>().eq(TradeOrder::getOrderNo, orderNo));
            statusLogMapper.insert(logOf(orderNo, TradeOrder.STATUS_PROCESSING, TradeOrder.STATUS_FAIL,
                    cur == null ? null : String.valueOf(cur.getUserId()), "异步下单失败：" + reason));
        }
    }

    /** 网络类瞬时故障（如调用券服务超时）视为可重试，保持“处理中” */
    private boolean isTransient(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof java.net.ConnectException
                    || cur instanceof java.net.SocketTimeoutException
                    || cur.getClass().getName().contains("RetryableException")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    @Override
    public TradeOrder queryOrderStatus(Long userId, String orderNo) {
        return mustOwn(userId, orderNo);
    }

    @Override
    public List<TradeOrder> queryMyOrders(Long userId, Integer status) {
        LambdaQueryWrapper<TradeOrder> wrapper = new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getUserId, userId);
        if (status != null) {
            wrapper.eq(TradeOrder::getStatus, status);
        }
        wrapper.orderByDesc(TradeOrder::getCreateTime);
        return list(wrapper);
    }

    @Override
    public OrderDetailDTO queryOrderDetail(Long userId, String orderNo) {
        TradeOrder order = mustOwn(userId, orderNo);
        List<TradeOrderItem> items = tradeOrderItemMapper.selectList(
                new LambdaQueryWrapper<TradeOrderItem>().eq(TradeOrderItem::getOrderNo, orderNo));
        List<TradeOrderStatusLog> logs = statusLogMapper.selectList(
                new LambdaQueryWrapper<TradeOrderStatusLog>()
                        .eq(TradeOrderStatusLog::getOrderNo, orderNo)
                        .orderByAsc(TradeOrderStatusLog::getCreateTime));
        OrderDetailDTO detail = new OrderDetailDTO();
        detail.setOrder(order);
        detail.setItems(items);
        detail.setStatusLogs(logs);
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long userId, String orderNo) {
        TradeOrder order = mustOwn(userId, orderNo);
        if (order.getStatus() == null || order.getStatus() != TradeOrder.STATUS_PENDING) {
            throw new RuntimeException("订单状态不允许支付");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean paid = update(new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, orderNo)
                .eq(TradeOrder::getStatus, TradeOrder.STATUS_PENDING)
                .set(TradeOrder::getStatus, TradeOrder.STATUS_PAID)
                .set(TradeOrder::getPayTime, now));
        if (!paid) {
            throw new RuntimeException("订单状态已变化，请刷新后重试");
        }
        statusLogMapper.insert(logOf(orderNo, TradeOrder.STATUS_PENDING,
                TradeOrder.STATUS_PAID, userId.toString(), "支付成功"));
        // 当前无配送：支付即完成（1→2），状态1为后续派送机制预留
        update(new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, orderNo)
                .eq(TradeOrder::getStatus, TradeOrder.STATUS_PAID)
                .set(TradeOrder::getStatus, TradeOrder.STATUS_FINISHED)
                .set(TradeOrder::getFinishTime, now));
        statusLogMapper.insert(logOf(orderNo, TradeOrder.STATUS_PAID,
                TradeOrder.STATUS_FINISHED, userId.toString(), "支付即完成（后续接入配送）"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, String orderNo) {
        TradeOrder order = mustOwn(userId, orderNo);
        if (order.getStatus() == null || order.getStatus() != TradeOrder.STATUS_PENDING) {
            throw new RuntimeException("仅待支付订单可以取消");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean cancelled = update(new LambdaUpdateWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, orderNo)
                .eq(TradeOrder::getStatus, TradeOrder.STATUS_PENDING)
                .set(TradeOrder::getStatus, TradeOrder.STATUS_CANCELLED)
                .set(TradeOrder::getCancelTime, now));
        if (!cancelled) {
            throw new RuntimeException("订单状态已变化，请刷新后重试");
        }
        statusLogMapper.insert(logOf(orderNo, TradeOrder.STATUS_PENDING,
                TradeOrder.STATUS_CANCELLED, userId.toString(), "用户取消订单"));

        // 用券订单：自动退券（失败则整体回滚取消）
        if (order.getVoucherOrderId() != null) {
            VoucherReleaseDTO release = new VoucherReleaseDTO();
            release.setUserId(userId);
            release.setVoucherOrderId(order.getVoucherOrderId());
            release.setOrderNo(orderNo);
            Result<Void> resp = voucherClient.releaseVoucher(release);
            if (resp == null || !Boolean.TRUE.equals(resp.getSuccess())) {
                throw new RuntimeException(resp == null ? "退券失败，请稍后重试" : resp.getErrorMsg());
            }
        }
    }

    private List<Cart> resolveSelectedCarts(Long userId, List<Long> cartIds) {
        List<Cart> all = cartService.list(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId));
        if (cartIds != null && !cartIds.isEmpty()) {
            return all.stream().filter(c -> cartIds.contains(c.getId())).collect(Collectors.toList());
        }
        return all.stream()
                .filter(c -> c.getSelected() != null && c.getSelected() == 1)
                .collect(Collectors.toList());
    }

    /** 订单号：yyyyMMdd + Redis 当日自增序列（6位），uk_order_no 唯一索引兜底 */
    private String genOrderNo() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long seq = stringRedisTemplate.opsForValue().increment("icr:trade:order:" + date);
        if (seq == null) {
            throw new RuntimeException("订单号生成失败，请稍后重试");
        }
        return date + String.format("%06d", seq % 1000000L);
    }

    private TradeOrderStatusLog logOf(String orderNo, Integer from, Integer to, String operator, String remark) {
        return new TradeOrderStatusLog()
                .setOrderNo(orderNo).setFromStatus(from).setOrderStatus(to)
                .setOperator(operator).setRemark(remark);
    }

    private TradeOrder mustOwn(Long userId, String orderNo) {
        TradeOrder order = getOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, orderNo)
                .eq(TradeOrder::getUserId, userId));
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        return order;
    }
}

