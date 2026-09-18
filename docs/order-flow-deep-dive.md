# &#x20;IntellRecipe 订单链路深度解析（面试备战版）

> 对应 commit `08ff038`（v2.0.0 交易闭环）+ 2026-09-07 补偿工程化改造。  
> 所有细节以当前代码为准，文件位置见文末索引。

---

## 0. 三十秒总纲（面试开场白）

> 下单采&#x7528;**「受理-异步-补偿」三段式**：同步事务只做"受理"——校验、生成订单号、落库一笔 status=4（处理中）的订单和商品快照明细，然后投递 RabbitMQ 立即返回订单号；MQ 消费者与定时补偿任务**共用同一个幂等执行体** `processOrderCreate`，完成核销优惠券、推进 4→0（待支付）、清理购物车；瞬时故障走 MQ 延迟重投（30s×3）+ 补偿任务线性退避（2min×n），累计 10 次失败收敛为终态失败。一致性不靠分布式事务，靠**六道幂等防线 + 订单表当本地消息表 + 状态机全程留痕**，最终一致窗口最坏约 2 小时、正常亚秒级。



---

## 1. 状态机全集

### 1.1 订单状态（trade_order.status）

| 值 | 状态   | 性质          | 迁移触发                      | 关键时间戳       | clientToken |
| - | ---- | ----------- | ------------------------- | ----------- | ----------- |
| 4 | 处理中  | 中间态（**有界**） | 受理事务落库                    | create_time | 占坑          |
| 0 | 待支付  | 中间态         | 异步执行成功（CAS 4→0）           | —           | 占坑          |
| 1 | 已支付  | **瞬时态**     | 用户 `pay()`（CAS 0→1）       | pay_time    | 占坑          |
| 2 | 已完成  | 终态          | `pay()` 内紧接 1→2（为配送预留）    | finish_time | **释放**      |
| 3 | 已取消  | 终态          | 用户取消 / 30 分钟超时关单（CAS 0→3） | cancel_time | **释放**      |
| 5 | 下单失败 | 终态          | 业务失败，或瞬时故障重试 10 次超限       | fail_reason | **释放**      |

- 每次迁移都写 `trade_order_status_log`（from_status → order_status + operator + remark），构成前端可渲染的全链路时间线。
- status=4 的**有界性**是设计关键：瞬时失败期间订单可以卡在 4，但重试计数（retry_count）保证最坏约 2 小时内必然收敛到 0 或 5，不会永久滞留。
- status=1 是瞬时态（当前"支付即完成"无配送），面试可答：预留配送中间态，接入派送后由配送回调推进 1→2。

### 1.2 券状态（voucher_order.status）

| 值 | 含义      | 迁移                          |
| - | ------- | --------------------------- |
| 1 | 待支付（刚领） | 支付后变 2                      |
| 2 | 已支付（可用） | 核销 CAS →3；退券 CAS ←3         |
| 3 | 已核销     | `used_order_no` 记录核销到哪笔商城订单 |

---

## 2. 阶段一：同步受理 `createOrders(userId, dto)`

### 2.1 逐步细节

```
① 参数校验
   收货人/电话/地址任一为空 → 直接抛"请填写完整的收货人/电话/地址"
   注意：没有 @Valid，手动校验（可改进点）

② clientToken 归一化
   final String clientToken = isNotBlank(dto.getClientToken()) ? dto.getClientToken() : null
   前端语义：一次结算会话一个 token（进结算页生成，存 sessionStorage）

③ 幂等前置查询（快路径）
   SELECT ... WHERE user_id=? AND client_token=? AND status IN (4,0) LIMIT 1
   命中 → log + 直接返回原订单号，结束（防双击/超时重试）
   为什么只查 (4,0)：终态订单的 token 已被释放（置 NULL），语义 = "同一意图只允许一笔在途"

④ 购物车解析 resolveSelectedCarts
   cartIds 非空 → 按传入 id 过滤该用户全部购物车
   cartIds 为空 → 取 selected=1 的勾选项
   为空 → 抛"没有可结算的商品"

⑤ 券预校验 precheck（只读，不核销！）
   Feign → voucher-service /internal/voucher/precheck
   校验：归属当前用户、status∈{1,2}、未过期 → 返回券简况（含 shopId）
   再校验：券所属商家必须出现在本单购物车里，否则"所选优惠券不适用于本次结算的商品"
   为什么受理时只 precheck 不核销：核销放在异步执行体里，受理失败（如 MQ 投递后异步失败）
   时券已经退回，避免"受理成功但券白扣"的窗口

⑥ 生成订单号 genOrderNo()
   yyyyMMdd + Redis INCR icr:trade:order:{date} 取模 1000000，格式化 6 位
   生成放事务外（Redis 调用不占 DB 连接）；冲突概率极低，撞了由 DuplicateKey 分支换单号重试

⑦ 事务落库 doCreate → TransactionTemplate.executeWithoutResult(lockAndWriteOrder)
   - 批量查商品 listByIds，构建 productMap（key 冲突取第一个，防 Map 重复 key 异常）
   - 逐项校验：商品不存在 → 抛错；status≠1（下架）→ 抛"商品已下架：xxx"
   - 价格快照：price/subtotal 全部 setScale(2, HALF_UP)，total 累加后再 scale
   - 构造 TradeOrder：merchantId/merchantName 置 NULL（整单模型，商家维度在明细里）、
     voucherMerchantId（券所属商家，用于异步时按该商家小计校验门槛）、
     totalAmount/discountAmount=0/payAmount=total、status=4、clientToken
   - save 主单 → 逐条 insert 明细（商品名/图/单价快照）→ 插状态日志(null→4 "下单受理")
   注意：逐条 insert 非 saveBatch（已知取舍）；无库存扣减（已知取舍）

⑧ 投递 MQ sendOrderCreate
   rabbitTemplate.convertAndSend(ORDER_EXCHANGE, ROUTING_KEY, orderNo)
   消息体只有订单号 —— 真正的数据在 DB 里（这是"订单表=本地消息表"模式）
   失败仅 log.error 不抛出 → 用户已拿到订单号，2 分钟后补偿任务扫描重放
   为什么敢吞：受理已落库，消息丢了不丢单，只是慢

⑨ 返回 singletonList(orderNo) → 前端轮询 /order/status（20 次 × 700ms）
```

### 2.2 DuplicateKeyException 三分支分流（catch 核心）

唯一索引 `uk_user_client_token(user_id, client_token)` 或 `uk_order_no(order_no)` 撞了都会进 catch。**不能笼统当作"订单号冲突"**（那是原版 bug：终态单占 token → 换单号重试 → 再撞 → 用户看到 MySQL 报错且永久无法下单）。正确分流：

```
catch (DuplicateKeyException):
  ├─ clientToken 非空 → 按 (user_id, token) 不限状态回查：
  │    ├─ 查到在途单(4/0) → 幂等命中：返回原单号
  │    │   （status=4 还补发一次 MQ —— 原消息可能丢了）
  │    └─ 查到终态单 → 并发窗口/历史脏数据 → 本次提交置空 token 重建
  │        （绝不换单号重试：token 还占着坑，换单号会二次撞索引）
  └─ 按 token 查不到 → 才是真正的订单号冲突 → 换新单号重试一次
```

细节：回查用普通 SELECT（createOrders 无外层事务，每次查询新快照）→ 能读到并发线程刚提交的数据；`doCreate(token=null)` 的 insert 不会再撞 token 索引（唯一索引不约束 NULL）。二次 doCreate 不再包 try（极端下抛给 Controller，概率可忽略）。

---

## 3. 阶段二：异步执行体 `processOrderCreate(orderNo)`

**设计核心：MQ 消费者（OrderMqListener）与补偿任务（OrderCompensateTask）调用同一个方法**，重试逻辑只有一份，天然一致。

### 3.1 执行步骤

```
① 幂等闸门：orderNo 查不到 → 抛"订单不存在"；status != 4 → 直接 return
   （消息重复消费/已被别人处理 → 静默跳过）

② 开事务 TransactionTemplate，事务内重新读一次订单（double-check，
   防止前置检查和事务之间被并发方推进）

③ 核销优惠券（有券时）
   - 计算门槛基数 baseAmount：整单模型下若券属于某商家，
     取该商家明细小计之和（SELECT 明细 WHERE order_no AND merchant_id 再 sum）；
     无券商家维度则用 totalAmount
   - Feign useVoucher(VoucherUseDTO)：券侧校验归属/状态/过期/商家匹配/门槛（分比较），
     然后 CAS：UPDATE voucher_order SET status=3, used_order_no=? WHERE id=? AND
     status IN (1,2) AND used_order_no IS NULL
     - CAS 成功 → 返回抵扣额 payValue(分)/100
     - CAS 失败 → 回查：若 used_order_no == 本单号 → 幂等返回原抵扣额；
       否则"优惠券已被使用"
   - Feign 超时但服务端实际成功 → 重试时命中幂等分支 → 不双扣（自洽闭环）

④ CAS 推进 4→0：UPDATE trade_order SET status=0, discount_amount, pay_amount
   WHERE order_no=? AND status=4
   ★ 检查返回值 advanced：false = 并发输家（消息重投与补偿同时跑）→ 静默 return
     不写状态日志、不重复清购物车（自审修复点）
   赢家 → 插状态日志(4→0 "异步下单完成，待支付")

⑤ 清理购物车：按本单明细的 productId（distinct）+ userId 删除，幂等

事务提交
```

### 3.2 异常分流（catch）

```
catch (Exception e):
  ① 券补偿：voucherUsed 标志为 true（核销成功但本地后续失败）→ 尽力 releaseVoucher
     失败只 log"需人工处理"（已知取舍：无本地补偿表）
  ② isTransient(e) 判定：沿 cause 链找 ConnectException / SocketTimeoutException /
     RetryableException（Feign）
     ├─ 非瞬时（业务失败）→ markOrderFail：CAS 4→5 + fail_reason + 释放 clientToken + 日志
     └─ 瞬时 → incrRetryCount（setSql retry_count+1，eq status=4 防并发计数错乱）
              → count >= 10 → markOrderFail("重试 10 次仍失败...")（收敛！）
  ③ rethrow → 让调用方（消费者/补偿）感知失败
```

为什么瞬时失败时**故意不释放 token**：订单还在途，补偿正在救它；此时释放会导致同 token 重试创建第二笔订单。用户换意图（重进结算页）拿的是新 token，不受影响——唯一索引是二元组，旧 token 只锁自己的会话。

---

## 4. 重试体系（三层梯度）

| 层    | 机制                                                                                                                                                                  | 参数                                   | 覆盖场景             |
| ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------ | ---------------- |
| MQ 层 | 延迟重试队列 `trade.order.retry.queue`：x-message-ttl=30s + 死信路由回主交换机（routing key 回主队列）；消费失败时若 isTransient 且 x-retry-count<3 → 带 `x-retry-count+1` 头重投（**持久化消息**）+ ACK 原消息 | 30s × 3 次                            | 瞬时故障的快速重试        |
| 补偿层  | `OrderCompensateTask` 每分钟扫 `status=4 AND update_time < NOW() - (retry_count+1)*2 MINUTE` LIMIT 50 → 重放 processOrderCreate                                           | 线性退避 2/4/6.../20min                  | MQ 不可用、消息丢失、服务重启 |
| 收敛   | incrRetryCount 达 10 → markOrderFail + 释放 token                                                                                                                      | 最坏 ≈1.5min + 2+4+6+8+10+12+14 ≈ 1 小时 | 保证"在途"有界         |

- 退避原理：`retry_count` 递增会刷新 `update_time`（ON UPDATE CURRENT_TIMESTAMP），下一轮扫出的条件自动从上次失败时刻起算，无需额外的 next_retry_time 列。
- 两层计数共享同一个 `retry_count`：MQ 3 次计入总数，补偿最多再 7 次。
- DLQ（trade.order.dlq）：重试超限的业务失败消息进 DLQ 留痕，**当前无消费者/告警**（已知取舍）。

---

## 5. 六道幂等防线（面试重点）

| # | 机制                                                | 位置             | 防什么                   | 失效兜底            |
| - | ------------------------------------------------- | -------------- | --------------------- | --------------- |
| 1 | clientToken 前置查询                                  | createOrders ③ | 同一意图重复受理（快路径，挡 99%）   | 挡不住并发窗口         |
| 2 | `uk_user_client_token` 唯一索引                       | DB             | 并发窗口内的重复 insert（最后防线） | DuplicateKey 分支 |
| 3 | 订单号唯一 `uk_order_no` + Redis INCR                  | DB/Redis       | 单号重复、重复生成             | 换单号重试           |
| 4 | 券核销 CAS（status IN(1,2) AND used_order_no IS NULL） | 券服务            | 同一张券被多单重复核销           | 回查幂等返回原抵扣额      |
| 5 | 状态 CAS（eq status=4 / eq status=0）+ 幂等闸门           | 异步执行体          | 消息重复消费、双执行体并发推进       | 输家静默退出          |
| 6 | 清购物车按 productId 幂等删 / 退券幂等放行                      | 执行体/券服务        | 副作用重复执行               | —               |

核心思想：**check-then-act 的竞态窗口必须有 DB 约束兜底**。前置查询是优化，唯一索引/CAS 才是正确性保证。

---

## 6. 并发场景推演（面试拷问高发区）

**Q1 双击提交（同 token 两请求并发）**  
两请求都过前置查询（都查不到）→ 都 insert → InnoDB 唯一索引使 insert 串行化（②等①提交后报 duplicate）→ ② catch 回查命中①的在途单 → 幂等返回同一单号。结果：一笔订单。✓

**Q2 两个标签页/两设备（不同 token）**  
二元组不同 → 两笔订单并存。边缘影响：同一张券只能被一笔核销（券 CAS），另一笔券核销失败走失败流程；购物车按 productId 删可能交叉清理（只影响展示）。mcdmc 用 Redis 锁挡此场景，本项目接受并存（已知取舍）。

**Q3 MQ 消息重复投递（at-least-once）**  
第二次消费时 status 已不是 4 → 幂等闸门 return → ACK。✓

**Q4 消费者与补偿任务同时处理同一单**  
都过闸门 → 都进事务 → 券核销幂等 → CAS 4→0 只有一个赢 → 输家 advanced=false 静默退出（本次修复）→ 无重复日志。✓

**Q5 支付与取消并发（用户两个页面同时点）**  
两者都 CAS `eq status=0` → 只有一个成功；输家 update 影响 0 行 → 抛"订单状态已变化，请刷新后重试"。✓

**Q6 用户支付 vs 30 分钟超时关单**  
同上，都是 CAS 0→X，天然互斥。✓

**Q7 券核销 Feign 超时（本地不知道成功没成功）**  
isTransient → 不置终态 → 重试 → useVoucher 幂等分支返回原抵扣额 → 不双扣。若重试全失败收敛为终态 → voucherUsed 未置 true → 不退券？注意：超时时若服务端已核销成功，voucherUsed=false，不会走退券补偿——但订单置 5 终态时券仍处于已核销+used_order_no=本单状态。**这里有个缺口**：终态失败的券不会被自动退回（releaseVoucher 只在 voucherUsed=true 的补偿路径和 cancel 路径调用）。诚实回答：这是当前实现的一个残留不一致窗口，改进方向是 markOrderFail 时若 voucherOrderId 非空则统一触发 releaseVoucher（它幂等：非本单核销会拒绝，本单核销会退回）。

**Q8 Redis 挂了（订单号生成不可用）**  
genOrderNo 抛异常 → 受理失败，用户收到"订单号生成失败，请稍后重试"。无降级（可改进：DB 号段/雪花兜底）。已产生的在途单不受影响。

**Q9 应用重启（处理中订单 + 在途 MQ 消息）**  
消息持久化在 durable 队列，重启后继续消费；即使队列也丢了（极端），补偿任务扫订单表重放。这就是"订单表=本地消息表"的价值：**事实先落库，恢复手段与 MQ 无关**。

---

## 7. 故障恢复矩阵

| 故障            | 用户感知                    | 恢复路径                  | 数据一致性    |
| ------------- | ----------------------- | --------------------- | -------- |
| MQ 投递失败       | 正常拿到单号，状态停留"处理中"最长 2min | 补偿任务重放                | 最终一致     |
| 消费者业务失败（券没了）  | 订单变"下单失败"，可重新结算         | 立即置 5 + 释放 token      | 立即一致     |
| 券服务宕机（瞬时）     | "处理中"停留，自动重试            | 30s×3 + 退避×7 → 成功或置 5 | 最坏 1h 收敛 |
| 券服务宕机（持续 >1h） | 订单置"下单失败"               | retry_count 上限收敛      | 最终一致     |
| MQ 整个挂掉       | 同上                      | 补偿任务与 MQ 无关           | 最终一致     |
| 应用重启          | 无感                      | durable 消息 + 补偿双保险    | 不丢       |
| DB 主从延迟       | 轮询短暂读旧状态                | 前端轮询 14s              | 自愈       |

---

## 8. 诚实清单：当前没做的（面试官追问时的加分答案）

1. **无库存扣减**：只校验上架状态，无超卖保护。正确做法：`UPDATE product SET stock=stock-N WHERE id=? AND stock>=N` 条件扣减，或 Redis 预扣 + 异步落库。
2. **支付是模拟的**：无支付网关、无 payment 流水表、无回调验签。mcdmc 的做法：PAY_SUCCESS_FOR_UPDATE_FAIL(33) 悬挂态 + ACTIVE_QUERY 主动反查回捞，值得借鉴。
3. **无分布式事务框架**：全靠补偿。若要更强保证：本地消息表（本项目单表已近似）或 Seata AT/TCC。
4. **Feign 在事务内**：远程调用占着 DB 连接，下游慢会拖垮连接池。改进：先查券再开事务，或事务拆小。
5. **DLQ 无告警**：坏了没人知道。
6. **整单模型商家维度查询缺索引**：trade_order_item 只有 idx_order_no，按 merchant 对账需补索引。
7. **定时任务无分布式锁**：多实例会重复扫描，靠 CAS 幂等兜底不重复执行副作用，但浪费。改进：ShedLock。

---

## 9. 面试拷问 Q\&A 速答卡

**Q：为什么不用 Seata？**  
A：这条链路的写冲突面很小（一张券、一笔订单），用"本地事务 + MQ + 补偿"的最终一致足够，且避免了 Seata AT 模式的全局锁与侵入性。学习成本与运维成本不匹配当前规模。mcdmc 同规模生产项目也不用 Seata，用的是物化补偿任务表。

**Q：为什么先落库再发 MQ，而不是先发 MQ 消费端建单？**  
A：① MQ 投递可能失败，先落库才有补偿依据（订单表即本地消息表，transactional outbox 思想）；② 同步受理能立刻做商品/券校验，用户体验好；③ 前端轮询 /order/status 要求订单已存在。若消费端建单，双击会产生两个不同单号两条消息，`uk_order_no` 一次也不会冲突，防重失效。

**Q：clientToken 为什么不能用订单号替代？**  
A：防重键必须是"重试时能原样再带上"的东西。订单号由服务端受理时现生成，重试场景下客户端根本不知道上次生成了什么（响应可能丢了），每次重试都是新单号——用输出当防重键等于没防。幂等键必须由客户端在第一次请求前确定。

**Q：订单号为什么这么设计？有什么问题？**  
A：yyyyMMdd + Redis 当日自增取模 100 万。优点：趋势递增、可读、分布式安全。问题：① 单日超 100 万单回绕（撞了有换单号重试兜底，但会越来越频繁）；② Redis key 无 TTL 永久累积；③ Redis 挂了无降级。生产改进：号段模式或Leaf。

**Q：为什么选 RabbitMQ 的 TTL+DLX 做延迟重投，而不是死信队列直接重试？**  
A：RabbitMQ 没有 RocketMQ 原生的 RECONSUME_LATER 重试梯度。TTL 过期 + 死信路由回主交换机是社区标准做法（无需延迟插件）；自定义 header x-retry-count 随死信保留，实现计数。注意点：消息必须持久化、重试队列不设消费者。

**Q：最终一致的时间窗口多长？怎么向业务方解释？**  
A：正常亚秒级（MQ 消费毫秒级）。最坏：券服务持续宕机时约 1 小时收敛为终态失败（3×30s + 退避 2+4+...+14min）。用户侧始终有明确状态可查（处理中/待支付/失败），不会出现"既不是成功也不是失败"的黑洞——因为 status=4 是有界的。

**Q：clientToken 被伪造怎么办？**  
A：唯一索引是 (user_id, client_token) 二元组，前置查询也 eq userId——伪造别人的 token 只会在自己账号下撞索引或查不到，无法影响他人订单。且 token 是随机 UUID，不可枚举。

**Q：如果让你重构，最大的改动是什么？**  
A：① 补库存条件扣减；② 引入 payment 流水表 + 支付回调驱动状态；③ 把券核销挪出事务（先查后核，或用本地消息表驱动）；④ 补偿独立任务表 + 归档（对齐 mcdmc），单表模型在数据量上来后扫描压力会显现。

---

## 10. 关键代码索引

| 内容                    | 位置                                                                                                |
| --------------------- | ------------------------------------------------------------------------------------------------- |
| 受理 + DuplicateKey 分流  | `item-service/.../service/impl/OrderServiceImpl.java` → `createOrders` / `doCreate`               |
| 落库快照                  | 同上 → `lockAndWriteOrder`                                                                          |
| 异步执行体（幂等/CAS/退券/计数）   | 同上 → `processOrderCreate` / `incrRetryCount` / `markOrderFail` / `isTransient`                    |
| 支付/取消（CAS + 释放 token） | 同上 → `pay` / `cancel`                                                                             |
| MQ 拓扑（主/DLX/重试队列）     | `item-service/.../config/OrderRabbitConfig.java`                                                  |
| 消费者（手动 ACK + 延迟重投）    | `item-service/.../mq/OrderMqListener.java`                                                        |
| 补偿任务（线性退避）            | `item-service/.../task/OrderCompensateTask.java`                                                  |
| 超时关单                  | `item-service/.../task/OrderExpireTask.java`                                                      |
| Feign 契约              | `intellrecipe-api/.../client/VoucherClient.java`                                                  |
| 券核销 CAS               | `voucher-service/.../service/impl/VoucherOrderServiceImpl.java` → `useVoucher` / `releaseVoucher` |
| 网关路由/限流               | `intellrecipe-gateway/.../config/SentinelConfig.java` + `application.example.yml`                 |
| 表结构/迁移                | `docker/mysql/init/schema.sql` + `migration/20260902*.sql` + `migration/20260907_order_retry.sql` |
| 前端结算页（token/轮询）       | `docker/nginx/html/checkout.html`                                                                 |
