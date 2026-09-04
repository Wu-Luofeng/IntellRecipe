# 商城订单闭环 + OpenFeign 跨服务方案（评审稿）

> 目标：购物车结算 → 订单生成（订单号唯一）→ 优惠券抵现核销 → 模拟支付 →
> 订单可追溯（明细 + 状态时间线）→ 事务一致。同时引入 **OpenFeign** 打通
> item-service 与 voucher-service，并把业务实体从 common 归还到各自服务。

---

## 1. 背景与问题

- 现状：购物车在 item-service，但 `checkout()` 是占位符；商品无库存概念；
  优惠券为「用户买/秒杀券」体系（voucher_order），尚无结算抵现路径；
  数据库无商城订单表。
- 问题 1：全部业务实体（Cart/Product/Voucher/VoucherOrder/Merchant…）堆在
  intellrecipe-common，域边界模糊，服务间靠“共享实体 + 直接读写”隐式耦合。
- 问题 2：没有服务间 RPC 基建，跨服务拿券/核销无标准通道。

---

## 2. 目标架构

```
浏览器/前端
   │ /api/*（网关 10010 分发）
   ▼
gateway ─────────────┬──────────────────────────────┐
                     ▼                              ▼
             item-service                     voucher-service
   购物车/商品/商家/商城订单          优惠券模板/用户券/核销
                     │                                │
                     └──── OpenFeign (lb://voucher-service)
                          VoucherClient: 可用券/核销/退券
```

### 2.1 新增模块 `intellrecipe-api`（服务间 Feign 契约，避免实体共用）
只放 Feign 接口 + 传输 DTO（贫血对象），供**调用方**依赖：

| 文件 | 说明 |
|---|---|
| `api/client/VoucherClient` | `@FeignClient(name="voucher-service")`：`useVoucher / releaseVoucher / usableVouchers` |
| `api/dto/VoucherUseDTO` | 核销请求（userId/voucherOrderId/shopId/orderNo/orderAmount） |
| `api/dto/VoucherReleaseDTO` | 退券请求（userId/voucherOrderId/orderNo） |
| `api/dto/UsableVoucherDTO` | 可用券查询返回（voucherOrderId/voucherId/title/payValue/actualValue/shopId/expireTime/status…） |

- 网关路由 `/voucher/**` 仍由前端直连 voucher-service；Feign 只服务**服务间**。

### 2.2 实体归属（核心调整：业务实体回到各服务）

| 现位置(common) | 迁往 | 理由 |
|---|---|---|
| `Result` / `UserDTO` / `UserHolder` | **common 保留** | 全局响应 / 登录上下文跨服务 |
| 拦截器 / RedisIdWorker / CacheClient / RedisConstants / RegexUtils | common 保留 | 通用基础设施 |
| `User` + UserLogin/ProfileDTO | user-service | 用户域 |
| `Product` `Cart` `Merchant` `Ingredient`(+DTO) | item-service | 商品/商家/购物车域 |
| `Voucher` `VoucherOrder` `SeckillVoucher` `MyVoucherDTO` `VoucherOrderDTO` | voucher-service | 券域 |
| `TradeOrder` `TradeOrderItem` `TradeOrderStatusLog`（新增） | item-service | 订单域（由购物车结算产生） |

- item-service **不再依赖 voucher-service 的实体**，券数据一律通过 Feign DTO。
- voucher-service 展示数据（`selectMyVouchers` 的 SQL join merchant）仍属自身实现细节。

---

## 3. 数据库变更（已定稿，执行一次即可）

`docker/mysql/init/migration/20260902_trade_order.sql`（幂等）：
1. `voucher_order` 增加 `used_order_no varchar(32)`、`used_order_id bigint`（核销关联商城订单，供退券追溯）；
2. 新表：
   - `trade_order`：主单（order_no 唯一、user、merchant 快照、total/discount/pay、券快照、收货快照、状态 0待支付1已支付2已完成3已取消、pay/finish/cancel 时间）
   - `trade_order_item`：商品快照明细（order_no 关联）
   - `trade_order_status_log`：状态时间线（from_status→order_status→operator→remark→time）
   - `schema.sql` 已同步基线（新库全量初始化时自动建）。

> ⚠️ 您需要在 DataGrip 对**云数据库**执行一次该迁移脚本（我不远程执行 SQL）。

---

## 4. 关键设计

### 4.1 订单号唯一
- `orderNo = yyyyMMdd + Redis INCR("icr:trade:order:<date>") 补 6 位`
- 数据库 `uk_order_no` 唯一索引兜底；INCR 失败/冲突时重试一次。
- 与现有优惠券雪花 id（`voucher_order.id`）相互独立、不相混淆。

### 4.2 优惠券使用（结算抵现）
状态模型（voucher_order.status）沿用：`1待支付 2已支付(可用) 3已核销`。
- 结算页：`GET /voucher-orders/usable?shopId=`（登录用户 + 商家）→ 展示可用券；
- 下单：item-service 调 Feign `useVoucher`：
  - 校验：券归属本人、状态 ∈{1,2}、未过期、`voucher.shop_id = 本单商家`、`orderAmount ≥ actual_value` 门槛；
  - **原子幂等**：`UPDATE ... SET status=3, used_order_no=?, use_time=NOW() WHERE id=? AND user_id=? AND status IN(1,2) AND used_order_no IS NULL`
    - 影响 0 行时重查：若 `used_order_no == 本单号` → 幂等返回原抵扣；否则抛“券已被使用”；
  - 返回抵扣额 `pay_value/100`（元）。
- 取消订单：Feign `releaseVoucher`：仅当 `status=3 且 used_order_no=本单号` 才退回 `status=2` 并清核销标记（券可再次使用）。

### 4.3 事务与一致性（异步下单，订单表即本地消息表）
`POST /order/create` 只做**同步受理**：
1. 解析待结算购物车，可选 `precheck` 确认券归属商家；按现价校验并计算金额；
2. 每个商家一个本地事务：写 `trade_order`(status=处理中=4) + `trade_order_item`(快照)，随后**仅把订单号**投递 MQ；
3. 立即返回订单号；前端可轮询 `GET /order/status`。

MQ 消费者 / 定时补偿调用 `processOrderCreate(orderNo)`（幂等）：
- 仅处理“处理中”订单：核销券(voucher-service, 幂等) → 回填折扣/实付 → 状态推进为“待支付(0)” → 清理购物车；
- 失败处理：已核销券补偿退回；业务失败置“下单失败(5)”并写 failReason；网络类瞬时失败保持“处理中”待补偿重试。

**定时补偿**：扫 `status=4 AND create_time 超时`（索引 `(status, create_time)`），重放 `processOrderCreate`。

> 一致性说明：券核销与订单状态分属两服务，采用「核销先行 + 失败补偿」最终一致；MQ（durable 消息 + confirm + 手动 ACK）与**订单表自留痕** + 定时补偿共同保证“订单不丢”。

### 4.4 订单可追溯
- 明细快照（下单后商品改名/下架不影响订单展示）；
- `trade_order_status_log` 记录每个动作（下单/支付/完成/取消：from→to、操作人、时间）；
- `GET /order/detail?orderNo=` 一次返回 主单 + 明细 + 时间线。

### 4.5 状态机（模拟支付，无真实资金）
订单状态：`4 处理中 → 0 待支付 → 1 已支付 → 2 已完成`；失败终态 `5 下单失败`；取消 `3 已取消`。
```
4 处理中 ─(异步下单完成)─▶ 0 待支付 ──pay──▶ 1 已支付 ─(自动完成)──▶ 2 已完成
  │                                      │
  │(业务失败)                            └──cancel──▶ 3 已取消（用券自动退券）
  └─▶ 5 下单失败（fail_reason）
```
- `pay`：0→1 支付成功，随即自动 1→2 完成（当前无配送 = **支付即完成**；状态 1 为后续“派送机制”预留中间态）；
- `cancel` 仅允许 0→3；`processOrderCreate` 只处理 4 的订单（幂等）；
- 每次变更都写入 `trade_order_status_log`，可追溯。


---

## 5. 后端接口清单

### item-service（网关前缀 `/order/**`，需登录）
| 方法/路径 | 说明 |
|---|---|
| `POST /order/create` | body `CreateOrderDTO`（cartIds 可空=勾选项、voucherOrderId、收货人/电话/地址、备注）→ 返回 `orderNo[]` |
| `GET /order/list?status=` | 我的订单列表（倒序） |
| `GET /order/detail?orderNo=` | 订单详情（主单+明细+状态时间线） |
| `POST /order/pay?orderNo=` | 支付（0→1→2 支付即完成） |
| `POST /order/cancel?orderNo=` | 取消 0→3（自动退券） |

### voucher-service
| 方法/路径 | 说明 | 鉴权 |
|---|---|---|
| `GET /voucher-orders/usable?shopId=` | 我的可用券（结算页下拉用） | 登录 |
| `POST /internal/voucher/use` | 核销（Feign 目标） | 内部（前缀避开登录拦截器） |
| `POST /internal/voucher/release` | 退券（Feign 目标） | 内部 |

### item-service 依赖调整（pom）
- 新增 `spring-cloud-starter-openfeign` + `spring-cloud-starter-loadbalancer`
- 新增模块 `intellrecipe-api` 依赖
- 启动类 `@EnableFeignClients`

---

## 6. 前端

| 页面 | 内容 |
|---|---|
| `checkout.html` | 结算确认：勾选商品汇总、按商家展示、选可用券（下拉券/金额自动更新）、收货人信息、提交 |
| `orders.html` | 我的订单列表：订单号/商家/金额/状态、支付/取消按钮 |
| `order-detail.html` | 详情：明细 + 金额构成 + 状态时间线（可追溯） |
| `cart.html` | 「去结算」→ 收集勾选项跳转 checkout |
| `my-vouchers.html` | 联动展示可用券（可选增强） |

网关 item-service 路由增加 `/order/**`（本地 yml 与 example 模板同步）。

---

## 7. 实施步骤（建议顺序，可在评审后按此执行）

1. **基础设施**：新建 `intellrecipe-api` 模块；父 pom 加 module；
2. **实体归域重构（存量实体迁移，涉及全部服务 import 改动）**；
3. voucher-service：核销/退券/可用券 + 内部 REST（Feign 目标）+ 领域实体就位；
4. item-service：TradeOrder 域实体/DAO/Service/Controller + Feign 装配 + `@Transactional` 下单；
5. 网关路由 `/order/**` + 前端三页面 + cart 跳转；
6. 构建、启动全部服务、执行 DDL；
7. 联调：加购→结算用券→下单→支付→详情/取消退券、幂等与并发用例、事务回滚用例。

---

## 8. 需要您拍板的点

1. **存量实体迁移范围**：✅ 已确认 **方案 A（渐进）** —— 本次新增 TradeOrder 域放 item-service；存量 Cart/Voucher 等实体迁移留作独立重构提交。
2. DDL 由您在 DataGrip 执行（云库）。✅ 已执行 `docker/mysql/init/migration/20260902_trade_order.sql`
3. 状态机：✅ 已确认 **支付即完成**（保留状态 1 作派送预留）。
