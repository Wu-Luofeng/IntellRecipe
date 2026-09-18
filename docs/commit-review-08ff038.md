# Commit Review：08ff038 `v2.0.0 商城完整交易闭环`

- **提交**：`08ff038585a7da70b458cf634e8e9ade6f4de77c`
- **作者 / 时间**：Wu-Luofeng · 2026-09-04 12:04
- **分支**：`v2.0.0`
- **规模**：56 个文件，`+2880 / -8`（其中约 100+ 张商品图片占位，实际代码约 +2600）

---

## 一、改动涉及的模块

| 模块                     | 改动性质       | 关键内容                                                                                                                          |
| ---------------------- | ---------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `item-service`         | **主体（新增）** | 订单域全套：Controller / Service / Impl / 3 个 Entity / 3 个 Mapper / MQ 配置与消费者 / 2 个定时任务                                             |
| `voucher-service`      | 扩展         | 新增 `InternalVoucherController`（`/internal/voucher`），`VoucherOrderServiceImpl` 增加 `useVoucher` / `releaseVoucher` / `precheck` |
| `intellrecipe-api`     | **新增契约层**  | `VoucherClient`（OpenFeign）+ 4 个跨服务 DTO；`pom.xml` 引入 OpenFeign                                                                 |
| `user-service`         | 新增         | 收货地址簿：`UserAddress` / Mapper / Service / Impl / `AddressController`                                                           |
| `intellrecipe-gateway` | 修改         | 路由新增 `/order/**`、`/address/**`；Sentinel 限流阈值 5→100 QPS；429 响应强制 UTF-8                                                         |
| `intellrecipe-common`  | 微调         | `VoucherOrder` 增加 `usedOrderNo` / `usedOrderId`；`IngredientDTO` 加字段                                                           |
| `docker/mysql/init`    | 新增         | `schema.sql` 基线 + 5 个幂等增量迁移（v1~v5）                                                                                            |
| `docker/nginx/html`    | 新增         | `checkout.html`(338) / `orders.html` / `order-detail.html` / `addresses.html`                                                 |
| `deploy/local-dev`     | 新增         | Nacos / 隧道 / 前端代理 / 6 服务的一键起停脚本                                                                                               |
| 全局                     | 工程化        | 所有 `application.yml` → `application.example.yml`，本地配置不再入库                                                                     |

---

## 二、采用的架构

### 2.1 整体：微服务 + 事件驱动最终一致

```
前端 checkout.html
   │  clientToken（sessionStorage）
Gateway（Nacos 服务发现 + Sentinel 限流）
   │
item-service ──(OpenFeign / intellrecipe-api 契约)──> voucher-service
   │                                                      │
   ├── MySQL  trade_order / trade_order_item / trade_order_status_log
   ├── Redis  icr:trade:order:yyyyMMdd 自增（订单号）
   └── RabbitMQ  trade.order.exchange（durable + DLX）
```

### 2.2 核心：三段式异步下单（受理 → 异步 → 补偿）

不用 Seata / TCC，而是 **「本地事务写单 + MQ 驱动 + 订单表扫描兜底」** 的最大努力型最终一致：

1. **受理**：同步事务内写 `trade_order(status=4)` + 明细快照 + 状态日志，立即返回订单号；
2. **异步**：MQ 消费者执行 `processOrderCreate`，核销券 → 推进 `status=0` → 清购物车；
3. **兜底**：2 分钟补偿任务扫描滞留 `status=4` 的订单，重放同一执行体。

### 2.3 状态机

| 值 | 含义       | 触发             | 落库时间戳       |
| - | -------- | -------------- | ----------- |
| 4 | 处理中（已受理） | `createOrders` | create_time |
| 0 | 待支付      | MQ 消费 / 补偿任务   | —           |
| 1 | 已支付（瞬时态） | `pay()`        | pay_time    |
| 2 | 已完成      | `pay()` 紧接推进   | finish_time |
| 3 | 已取消      | 用户取消 / 30 分钟超时 | cancel_time |
| 5 | 下单失败（终态） | 异步执行非瞬时异常      | fail_reason |

每次迁移都写 `trade_order_status_log`（from → to + operator + remark），构成可追溯时间线。

### 2.4 幂等三保险

1. **前置查询**：`(user_id, client_token)` 且 `status ∈ (4,0)` 命中即返回原单；
2. **DB 唯一索引**：`uk_user_client_token (user_id, client_token)`；
3. **`DuplicateKeyException` 兜底**：捕获后回查原单并补发 MQ。

---

## 三、核心代码（简化呈现）

### 3.1 下单受理：幂等 + 事务 + MQ 投递

```java
public List<String> createOrders(Long userId, CreateOrderDTO dto) {
    // ① 幂等前置：同一 clientToken 已有「处理中/待支付」订单 → 返回原单
    if (StrUtil.isNotBlank(dto.getClientToken())) {
        TradeOrder existed = getOne(w -> w.eq(userId).eq(clientToken)
                                          .in(status, PROCESSING, PENDING).last("limit 1"));
        if (existed != null) return List.of(existed.getOrderNo());
    }

    List<Cart> selected = resolveSelectedCarts(userId, dto.getCartIds());

    // ② 券预校验（只读）：拿券所属商家，且必须出现在本单购物车中
    Long voucherShopId = null;
    if (dto.getVoucherOrderId() != null) {
        Result<VoucherBriefDTO> pcr = voucherClient.precheck(pc);
        voucherShopId = pcr.getData().getShopId();
        if (selected.stream().noneMatch(c -> shopId.equals(c.getMerchantId())))
            throw new RuntimeException("所选优惠券不适用于本次结算的商品");
    }

    // ③ 事务内落库（整单模型：一次结算 = 一个订单，明细保留各自商家）
    try {
        String orderNo = genOrderNo();                       // yyyyMMdd + Redis 自增 6 位
        tx.executeWithoutResult(s ->
            lockAndWriteOrder(userId, selected, dto, orderNo, voucherOrderId, voucherShopId));
        sendOrderCreate(orderNo);                            // 失败仅打日志 → 补偿任务兜底
        return List.of(orderNo);
    } catch (DuplicateKeyException e) {
        // ④ 并发重复提交 → 回查原单补发消息；真订单号冲突 → 重新生成再试一次
        TradeOrder dup = findByToken(userId, clientToken);
        if (dup != null) { if (dup.status == 4) sendOrderCreate(dup.getOrderNo()); return List.of(dup.getOrderNo()); }
        String retryNo = genOrderNo();
        tx.executeWithoutResult(s -> lockAndWriteOrder(..., retryNo, ...));
        sendOrderCreate(retryNo);
        return List.of(retryNo);
    }
}
```

**落库细节**（价格快照 + 下架校验）：

```java
private void lockAndWriteOrder(...) {
    Map<Long, Product> productMap = productService.listByIds(ids);
    for (Cart cart : carts) {
        Product p = productMap.get(cart.getProductId());
        if (p == null) throw new RuntimeException("商品不存在");
        if (p.getStatus() != 1) throw new RuntimeException("商品已下架：" + p.getName()); // 无库存校验
        BigDecimal price    = p.getPrice().setScale(2, HALF_UP);
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty)).setScale(2, HALF_UP);
        total = total.add(subtotal);
        items.add(new TradeOrderItem().setMerchantId(cart.getMerchantId())
                 .setProductName(p.getName())...);   // 快照：名称/图片/单价
    }
    save(order.setStatus(STATUS_PROCESSING).setMerchantId(null));  // 整单：主表商家置空
    items.forEach(tradeOrderItemMapper::insert);                   // 逐条插入，未批处理
    statusLogMapper.insert(logOf(orderNo, null, 4, userId, "下单受理（等待异步处理）"));
}
```

### 3.2 异步执行体（MQ 消费者与定时补偿共用，天然幂等）

```java
public void processOrderCreate(String orderNo) {
    TradeOrder order = getByOrderNo(orderNo);
    if (order.getStatus() != STATUS_PROCESSING) return;   // 幂等闸门

    final boolean[] voucherUsed = {false};
    try {
        tx.executeWithoutResult(s -> {
            TradeOrder cur = getByOrderNo(orderNo);
            if (cur.getStatus() != STATUS_PROCESSING) return;   // 并发下已被其它消息处理

            // 1) 核销券：多商家整单时按「券所属商家」的商品小计校验门槛
            BigDecimal discount = ZERO;
            if (cur.getVoucherOrderId() != null) {
                BigDecimal base = (cur.getVoucherMerchantId() != null)
                    ? sumSubtotalOf(orderNo, cur.getVoucherMerchantId())   // 该商家小计
                    : cur.getTotalAmount();
                Result<BigDecimal> resp = voucherClient.useVoucher(
                        new VoucherUseDTO(userId, voucherOrderId, shopId, orderNo, base));
                if (!resp.getSuccess()) throw new RuntimeException(resp.getErrorMsg());
                discount = resp.getData();
                voucherUsed[0] = true;
            }
            if (discount.compareTo(cur.getTotalAmount()) > 0) discount = cur.getTotalAmount();

            // 2) 条件更新推进为「待支付」（CAS，防止并发重复推进）
            update(w -> w.eq(orderNo).eq(status, PROCESSING)
                         .set(status, PENDING).set(discountAmount, discount)
                         .set(payAmount, total.subtract(discount)));
            statusLogMapper.insert(logOf(orderNo, 4, 0, userId, "异步下单完成，待支付"));

            // 3) 清理购物车（按 productId 删除，幂等）
            cartService.remove(w -> w.eq(userId).in(productId, productIdsOf(orderNo)));
        });
    } catch (Exception e) {
        if (voucherUsed[0]) { /* 券已核销但本地回滚 → 最大努力退券，失败仅告警 */ }
        if (!isTransient(e)) markOrderFail(orderNo, e.getMessage());  // 网络类异常保持 4 等待补偿
        throw e;
    }
}
```

**瞬时故障识别**（决定是否置终态失败）：

```java
private boolean isTransient(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause())
        if (c instanceof ConnectException || c instanceof SocketTimeoutException
            || c.getClass().getName().contains("RetryableException")) return true;
    return false;
}
```

### 3.3 MQ 基础设施（持久化 + 死信）

```java
@Bean Queue orderQueue() {
    return QueueBuilder.durable("trade.order.queue")
        .withArgument("x-dead-letter-exchange", "trade.order.dlx.exchange")
        .withArgument("x-dead-letter-routing-key", "trade.order.dlx").build();
}
@Bean DirectExchange orderExchange() { return new DirectExchange("trade.order.exchange", true, false); }
@Bean MessageConverter orderMessageConverter() { return new Jackson2JsonMessageConverter(); }
```

```java
@RabbitListener(queues = ORDER_QUEUE, ackMode = "MANUAL")
public void onCreate(String orderNo, Channel channel, @Header(DELIVERY_TAG) long tag) {
    try { orderService.processOrderCreate(orderNo); channel.basicAck(tag, false); }
    catch (Exception e) { channel.basicNack(tag, false, false); }  // 不 requeue，交给订单表补偿
}
```

### 3.4 券核销：原子 CAS + 幂等返回原抵扣额

```java
public BigDecimal useVoucher(VoucherUseDTO dto) {
    if (vo.getStatus() == 3) {                                  // 已核销
        if (dto.getOrderNo().equals(vo.getUsedOrderNo())) return discountOf(id); // 幂等重放
        throw new RuntimeException("优惠券已被使用");
    }
    if (!voucher.getShopId().equals(dto.getShopId())) throw ...;                 // 商家隔离
    if (门槛校验) orderAmount*100 < actualValue → throw "订单金额未达到门槛";        // 分单位

    boolean updated = update(w -> w.eq(id).eq(userId).in(status, 1, 2)
                                   .isNull(usedOrderNo)                          // CAS 条件
                                   .set(status, 3).set(useTime, now)
                                   .set(usedOrderNo, dto.getOrderNo()));
    if (!updated) { /* 回查：若是本单已占用 → 返回原抵扣额，否则抛「已被；使用」 */ }
    return discountOf(id);   // payValue（分） / 100
}
```

退券同理：仅当 `status==3 && usedOrderNo.equals(dto.getOrderNo())` 才回滚为 2，否则幂等放行。

### 3.5 支付 / 取消

```java
@Transactional(rollbackFor = Exception.class)
public void pay(Long userId, String orderNo) {
    if (order.getStatus() != PENDING) throw new RuntimeException("订单状态不允许支付");
    if (!update(w -> w.eq(orderNo).eq(status, PENDING).set(status, PAID).set(payTime, now)))
        throw new RuntimeException("订单状态已变化，请刷新后重试");   // CAS 防重复支付
    log(0 → 1, "支付成功");
    update(w -> w.eq(status, PAID).set(status, FINISHED).set(finishTime, now));  // 支付即完成
    log(1 → 2, "支付即完成（后续接入配送）");
}

@Transactional(rollbackFor = Exception.class)
public void cancel(Long userId, String orderNo) {
    if (!update(w -> w.eq(status, PENDING).set(status, CANCELLED).set(cancelTime, now))) throw ...;
    log(0 → 3, "用户取消订单");
    if (order.getVoucherOrderId() != null) {
        Result<Void> r = voucherClient.releaseVoucher(...);
        if (!r.getSuccess()) throw new RuntimeException("退券失败，请稍后重试"); // 退券失败 → 整体回滚
    }
}
```

### 3.6 两个定时任务

```java
// 补偿：滞留「处理中」超过 2 分钟的订单，重放执行体
@Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
void compensateProcessingOrders() {
    list(w -> w.eq(status, 4).lt(createTime, now.minusMinutes(2)).last("limit 50"))
        .forEach(o -> orderService.processOrderCreate(o.getOrderNo()));
}

// 超时：待支付超过 30 分钟自动关单（复用 cancel，自动退券）
@Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
void expirePendingOrders() {
    list(w -> w.eq(status, 0).lt(createTime, now.minusMinutes(30)).last("limit 50"))
        .forEach(o -> orderService.cancel(o.getUserId(), o.getOrderNo()));
}
```

### 3.7 数据模型（迁移脚本，全部幂等）

```sql
-- v1 主表
UNIQUE KEY uk_order_no (order_no), KEY idx_user_id, KEY idx_merchant_id, KEY idx_status
-- v2 异步化
ADD COLUMN fail_reason; ADD INDEX idx_status_time (status, create_time)   -- 供补偿扫描
-- v3 地址簿 user_address (is_default)
-- v4 整单模型
MODIFY merchant_id / merchant_name → NULL; ADD COLUMN voucher_merchant_id
-- v5 幂等
ADD COLUMN client_token; ADD UNIQUE KEY uk_user_client_token (user_id, client_token)
```

幂等写法（MySQL 5.7 无 `ADD COLUMN IF NOT EXISTS`）：

```sql
SET @ct := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE ... COLUMN_NAME='client_token');
SET @s  := IF(@ct = 0, 'ALTER TABLE ... ADD COLUMN ...', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
```

---

## 四、优点

1. **状态机 + 状态履历表**：每次迁移落 `trade_order_status_log`，订单全生命周期可追溯，前端直接渲染时间线，这是很多项目缺失的。
2. **异步执行体复用**：MQ 消费者与补偿任务调同一个 `processOrderCreate`，逻辑只有一份，避免"两条路径两套代码"。
3. **幂等设计层次清晰**：前置查询（快路径）+ DB 唯一索引（强约束）+ `DuplicateKeyException` 回查（兜底）+ CAS 条件更新（状态推进），四层防护。
4. **券服务核销是真正的原子操作**：`in(status,1,2) AND used_order_no IS NULL` 的条件更新，配合"同 orderNo 重放返回原抵扣额"，并发下既不会超扣也不会重复扣。
5. **明细快照化**：商品名/图/单价写入订单明细，后续商品改价下架不影响历史订单。
6. **故障分类处理**：`isTransient()` 区分网络抖动（保持处理中，等补偿）与业务失败（置终态 5，用户可重下），避免"一次超时把订单判死"。
7. **金额处理规范**：全程 `BigDecimal` + `setScale(2, HALF_UP)`，券的分/元转换集中在 `discountOf()`，并有 `discount > total` 的封顶保护。
8. **迁移脚本幂等**：用 `information_schema` 判断 + `PREPARE`，可重复执行，对手动运维友好。
9. **配置脱敏**：`application.yml` → `application.example.yml`，本地/云端配置不再入库，解决了一个实际的安全问题。
10. **细节修复到位**：网关 429 响应显式指定 `charset=utf-8`，修掉中文乱码；限流 5→100 QPS 解决联调自伤。

---

## 五、不足与风险（按严重度排序）

### P0 — 安全

**1. `/internal/voucher` 可能被公网直达**  
网关显式路由只有 `/voucher/**`、`/seckill/**`、`/vouchers/**`、`/voucher-orders/**`，看似没放通 `/internal/**`；但配置里 `spring.cloud.gateway.discovery.locator.enabled: true`，会为所有注册服务生成 `/\<serviceId\>/**` 的默认路由，即 `/voucher-service/internal/voucher/use` 很可能可达。而该接口**无鉴权**，`userId` 直接由请求体携带 —— 任何人可构造请求核销/退回他人的券。  
**建议**：网关加 `SetPath`/路由排除 `/internal/**`，或在网关加一个全局过滤器拒绝外部访问 `/internal/**`；同时服务间调用补一个共享密钥 Header。

### P1 — 正确性问题

**2. `clientToken` 唯一索引会导致"订单失败后无法重新下单"（真实 bug）**  
流程：下单成功但异步执行失败 → `status=5`。此时 `uk_user_client_token` 仍占着这个 token。用户返回结算页重试：

- 幂等前置查询只过滤 `status ∈ (4,0)` → 查不到 → 继续 insert；
- insert 撞唯一索引 → `DuplicateKeyException`；
- catch 里再查 `status ∈ (4,0)` → 仍查不到 → 走入"订单号冲突重试"分支；
- 用新订单号再插一次 → **再次撞唯一索引**，异常逃逸到 Controller，用户看到的是 MySQL 错误原文。  
  **建议**：catch 中若回查不到 `(4,0)` 的订单，说明该 token 已被终态订单占用 → 将新订单的 `clientToken` 置空（或让前端重新生成 token）后重试；或把唯一索引改为"部分唯一"（MySQL 8 可用函数索引，5.7 可用 `status` 参与组合：`uk (user_id, client_token, status)` 配合把终态订单的 token 清空）。

**3. 无库存扣减与并发校验**  
`lockAndWriteOrder` 只校验 `product.status == 1`，既不校验库存数量也不扣减库存，跨服务也没有锁。当前商品若是虚拟/无限库存尚可，一旦接真实库存必然超卖。  
**建议**：至少加 `stock` 字段的条件扣减（`UPDATE product SET stock=stock-N WHERE id=? AND stock>=N`），失败则下单失败；或引入冻结库存。

**4. 分布式事务不闭环，退券只是"最大努力"**  
`processOrderCreate` 中先 Feign 核销券（远程事务已提交），再改本地订单。若本地随后回滚，catch 里调 `releaseVoucher` 补偿 —— 但这次补偿**没有重试、没有落库留痕**，失败只打一行 `log.error("需人工处理")`。  
**建议**：把"待退券"写入本地表（如 `trade_order_compensate`），由定时任务重试；或用 RocketMQ 事务消息 / 本地消息表。

**5. 支付是"模拟支付"，缺支付流水**  
`pay()` 直接改状态，没有对接支付网关、没有 `payment` 流水表、没有外部订单号与回调验签。虽然 CAS 更新挡住了重复支付，但无法对账，也无法处理"支付成功但回调丢失"。  
**建议**：新增 `trade_payment` 表记录支付请求/回调，支付状态由回调驱动而非前端点击。

### P2 — 设计与健壮性

1. **定时任务无分布式锁**：`OrderCompensateTask` / `OrderExpireTask` 没有 `@SchedulerLock` 或 Redis 锁，多实例部署会重复扫描、重复执行。目前靠 CAS 更新保证幂等不出错，但属于"靠下游兜底"。建议引入 ShedLock。
2. **死信队列只留痕不处理**：`trade.order.dlq` 无消费者、无告警、无重投入口，堆积后无人知晓。建议加监控告警 + 管理端重投。
3. **`sendOrderCreate` 吞异常 + 前端轮询时长不匹配**：MQ 不可用时仅打日志，需等最长 2 分钟补偿；而前端 `poll()` 最多 20 × 700ms ≈ 14 秒就跳转 `orders.html`，用户会看到长时间停留在"处理中"。建议 MQ 投递失败时缩短补偿周期，或前端轮询失败后给用户明确提示。
4. **订单号生成的三个隐患**：① `seq % 1000000` 单日超 100 万单会回绕冲突（虽有重试但会越来越频繁）；② Redis key 无 TTL，永久累积；③ Redis 不可用直接抛异常，无数据库号段降级。
5. **`status=1 已支付` 是瞬时态**：`pay()` 里连续两次 update，`STATUS_PAID` 几乎不可观测，状态履历会多出一条时间差 <1ms 的记录。既然"当前无配送"，建议要么去掉 1，要么保留 1 并把推进到 2 的逻辑留给未来的配送回调，避免现在写了以后要改两处。
6. **整单模型的索引缺失**：主表 `merchant_id` 置空后，按商家维度查订单/对账只能走 `trade_order_item`，而该表只有 `idx_order_no`，没有 `idx_merchant_id`。且 `TradeOrder` 类注释仍写着"一个订单=一个商家一次结算"，与实现矛盾（文档债）。
7. **事务风格不统一**：`createOrders` 用 `TransactionTemplate` 手写事务，`pay/cancel` 用 `@Transactional`；明细插入逐条 `insert` 未用 `saveBatch`。功能无碍，但可维护性打折。
8. **地址簿无软删除、无上限**：`remove()` 是物理删除，若已产生的订单快照里引用过地址则无所谓（快照是 copy 的），但删除默认地址后自动把最新一条设为默认，逻辑略显随意；另外没有地址数量上限校验。
9. **前端金额计算用 JS Number**：`checkout.html` 里 `total - discount` 用浮点运算，与后端 `BigDecimal` 可能在分位产生差异。展示层可接受，但建议最终金额以后端返回的 `payAmount` 为准。

---

## 六、一句话总结

这是一个**完成度相当高的业务提交**：用"本地事务 + MQ + 订单表扫描补偿"的经典组合替代了分布式事务框架，幂等、状态机、可追溯、券的原子核销都做得扎实，还顺手补了配置脱敏和本地部署工程化。  
主要短板集中在三处：**`/internal/**` 的暴露面、`clientToken` 唯一索引与终态订单的冲突、以及缺失的库存与支付流水** —— 这三块补上，这条交易链路才真正具备生产可用性。
