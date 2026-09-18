# IntellRecipe 项目面试题库与标准答案

> 基于简历表述 + 仓库真实代码逐行核对整理。所有答案中的类名、常量、字段、返回值均对应实际实现，
> 可直接引用。**标 🔴 的是高频必考，标 ⚠️ 的是容易踩坑/需要主动暴露改进点的地方。**

---

## 目录

0. [项目介绍（开场必背）](#0-项目介绍开场必背)
1. [微服务架构](#1-微服务架构)
2. [登录与鉴权 🔴](#2-登录与鉴权-)
3. [高并发秒杀 🔴🔴🔴](#3-高并发秒杀-)
4. [分布式 ID](#4-分布式-id)
5. [缓存设计 🔴](#5-缓存设计-)
6. [Elasticsearch 搜索与降级](#6-elasticsearch-搜索与降级)
7. [游标分页](#7-游标分页)
8. [限流与网关](#8-限流与网关)
9. [异步下单与最终一致性（商城订单）🔴](#9-异步下单与最终一致性商城订单)
10. [部署与运维](#10-部署与运维)
11. [项目难点 / 亮点 / 不足](#11-项目难点--亮点--不足)
12. [通用技术追问](#12-通用技术追问)
13. [反问环节](#13-反问环节)

---

## 0. 项目介绍（开场必背）

### Q0.1 「介绍一下你这个项目」

**30 秒版本（推荐开场）：**

> IntellRecipe 是一个"内容 + 电商"模式的智能饮食平台，用户浏览食材/食谱后可以购买关联商品，
> 打通"浏览 → 加购 → 领券 → 下单"的闭环。整体是 Spring Cloud 微服务架构，网关统一入口、
> Nacos 注册发现，拆成了用户、商品、优惠券、饮食、食谱五个服务。技术难点主要集中在两块：
> 一是高并发秒杀，用 Redis + Lua 保证库存原子扣减防超卖，再异步化落库削峰；
> 二是检索与缓存，ES 做中文分词搜索并做了 MySQL 降级兜底，热点数据用逻辑过期防击穿。

**3 分钟版本（技术深挖时展开）：**

> 项目分五层讲：
> **架构上**，Gateway(10010) 做路由与限流，user-service(8081)、item-service(8082)、
> voucher-service(8083)、diet-service、recipe-service 分别承载各自领域，通过 Nacos 注册发现、
> OpenFeign 跨服务调用。
> **入口侧**，手机号 + 验证码登录，Token 存 Redis，双拦截器设计——第一层全局刷新 Token 有效期并
> 写入 ThreadLocal，第二层对下单等敏感接口强制校验登录态。
> **高并发侧**，秒杀是核心：Lua 脚本一次性完成"判库存 → 判重复 → 扣库存 → 记录用户 → 写 Stream"
> 五个动作，保证原子性；然后用 Redis Stream 做本地 Outbox，由 Dispatcher 转发到 RabbitMQ，
> 消费者异步落库，达到削峰和可靠性兼顾。订单 ID 用 Redis 自增序列 + 时间戳位拼接生成。
> **检索侧**，ES + IK 分词做中文模糊检索，ES 超时或不可用时自动降级到 MySQL 兜底；
> 首页热点数据用逻辑过期 + 互斥锁重建防缓存击穿，列表查询用游标分页避免深分页。
> **一致性侧**，商城下单走"本地事务受理 + MQ 异步推进 + 定时补偿"的最终一致方案，
> 幂等靠 clientToken 唯一索引，瞬时故障走 30 秒延迟重试，最多 3 次后转死信 + 补偿任务线性退避兜底。
> 中间件全部 Docker Compose 编排，做了低资源部署优化，4GB 内存服务器也能跑。

---

## 1. 微服务架构

### Q1.1 为什么拆微服务？单体不行吗？

**标准答案：**
> 拆分的驱动是**业务边界和伸缩性差异**。这个项目里不同模块的压力模型完全不一样：
> 秒杀接口瞬时 QPS 极高但对一致性要求可以放宽到最终一致；商品浏览是读多写少，适合加缓存；
> 饮食记录是低频写。如果做成单体，秒杀把线程池打满会直接拖垮商品浏览和登录，
> 而且扩容只能整体扩容，浪费资源。拆开之后我可以按服务独立扩容——比如秒杀只扩 voucher-service，
> 检索压力只扩 item-service。
>
> 另外从工程角度，多模块也方便按领域隔离数据，优惠券的库存表和商品表不会互相耦合。

**诚实补充（加分）：**
> 当然，如果是真实业务起步阶段，我可能不会一开始就上微服务，会先用模块化单体，
> 等瓶颈明确再拆。这个项目有学习和技术验证的目的，所以直接按微服务设计。

### Q1.2 服务之间怎么调用？怎么保证可用？

**标准答案：**
> 主要两种方式：
> 一是**OpenFeign 声明式调用**，我在 `intellrecipe-api` 模块里抽了 `VoucherClient`
> 接口和 DTO，item-service 下单时通过它调用 voucher-service 的券预校验、核销、退券接口，
> 接口和 DTO 复用同一份定义，避免两边字段对不上。
> 二是**RabbitMQ 异步解耦**，秒杀下单、商城下单这类不需要同步返回完整结果的链路走消息。
>
> 可用性上：Feign 走 Nacos 的服务发现 + 客户端负载均衡；异步链路靠 MQ 持久化 + 重试 + 补偿兜底。
> 不过限流熔断这块我只做了 Gateway 层的 Sentinel 热点参数限流，
> **Feign 调用还没有配置熔断降级，这是可以补的一环**（后面 Q11 会展开）。

### Q1.3 Gateway 做了什么？

**标准答案：**
> Gateway 是唯一入口，端口 10010，主要四件事：
> 1. **路由分发**：用 Path 断言把 `/user/**`、`/items/**`、`/voucher/**`、`/diet/**`、`/recipe/**`
>    转发到对应服务，`uri` 用 `lb://` 走负载均衡，配合 Nacos 的 `discovery.locator` 自动发现。
> 2. **热点参数限流**：自定义 GlobalFilter 集成 Sentinel（详见 Q8）。
> 3. **超时调优**：`httpclient.connect-timeout: 15000`、`response-timeout: 120s`，
>    这个是为下游慢查询和冷启动场景兜的，避免 Nginx 先 504。
> 4. **跨域处理**。

### Q1.4 Nacos 的作用？和 Eureka 有什么区别？

**标准答案：**
> Nacos 在我项目里做**注册中心**，服务启动时注册地址，Gateway 和 Feign 通过服务名拉取实例列表。
> 它相对于 Eureka 的优势是**一个组件同时做注册中心和配置中心**，支持配置的动态刷新不用重启；
> 而且它支持 AP/CP 模式切换，对临时实例用心跳的健康检查（AP），对持久化实例用 Raft（CP）。
> Eureka 只有 AP，且已经停止维护了。
> 另外 Nacos 有**服务分级存储模型**（服务 → 集群 → 实例），可以配同集群优先调用，
> 这对跨机房部署很有用。

---

## 2. 登录与鉴权 🔴

### Q2.1 讲讲你们的登录流程

**标准答案：**
> 手机号 + 验证码登录，完整链路是：
> 1. 前端请求 `/user/code`，后端校验手机号格式（`RegexUtils.isPhoneInvalid`），
>    调用阿里云号码认证服务（DYPNS）的 `SendSmsVerifyCode` 发送验证码。
>    **注意**：验证码是阿里云生成并下发的，不是我们本地生成的，我们只把返回的 code 存 Redis。
>    开发环境下 AccessKey 没配时 `isConfigured()` 返回 false，走本地随机 6 位数的 dev 模式，方便调试。
> 2. 验证码存 Redis：`login:code:{phone}`，TTL 2 分钟（`LOGIN_CODE_TTL = 2L`）。
> 3. 用户提交手机号 + 验证码，从 Redis 取出比对；比对成功后**立刻删除验证码**，防止重复使用。
> 4. 查 user 表，不存在则自动注册（昵称默认"用户"+6位随机串），实现"登录即注册"。
> 5. 生成 Token：`UUID.randomUUID().toString(true)`（32 位无横线 UUID），
>    把用户信息以 **Hash 结构**写入 Redis `login:token:{token}`，TTL 36000 秒（10 小时），
>    然后把 Token 返回给前端，前端后续请求放 `authorization` 头。

### Q2.2 为什么用 Redis + Token，不用 Session？🔴

**标准答案：**
> 三个原因，**核心是微服务下的共享状态问题**：
> 1. **Session 共享成本高**：多台 Tomcat 各自维护 Session，用户请求打到不同机器会丢失登录态，
>    需要 Session 复制或者 Spring Session 存 Redis，多一层组件。
> 2. **多端和跨域**：移动端、小程序不一定支持 Cookie，而 Token 放请求头没有这个限制。
> 3. **服务端可控**：Session 的过期和失效服务端不好主动干预；Token 存 Redis 后
>    我可以随时让它失效（删 key 即可），也方便做多设备登录管理、踢人下线。
>
> 我这个实现的本质是**把 Session 数据搬到了 Redis**，Token 只是 SessionId 的替代品，
> 属于"有状态 Token"。

**追问：那和 JWT 有什么区别？**
> JWT 是把用户信息签名后放客户端，服务端不存，无状态、省一次 Redis 查询；
> 但代价是**无法主动失效**——JWT 签发后不到期就一直有效，踢人、封号很难做。
> 我选 Redis 方案是因为业务需要服务端能控制登录态，而且用户信息（昵称、身高体重）
> 会更新，存 Redis 可以随时同步（我 `updateProfile` 里就同步更新了 Redis Hash）。
> 如果是对外开放 API 或者想省掉 Redis 依赖，JWT 更合适。

### Q2.3 双拦截器是怎么设计的？为什么？🔴

**标准答案：**
> 这是个很关键的设计。我用了两个拦截器，通过 `order` 控制顺序：
>
> **第一层 `RefreshTokenInterceptor`，order=0，`/**` 全路径**：
> - 取 `authorization` 头，为空直接放行（不拦未登录用户，因为很多接口不需要登录）
> - 用 `login:token:{token}` 查 Redis Hash
> - 查到就 `BeanUtil.fillBeanWithMap` 转成 `UserDTO`，存入 `UserHolder`（ThreadLocal）
> - **顺手刷新 Redis key 的 TTL**（`expire(key, 36000)`）——这就是"续期"，
>   用户只要一直在操作，就永远不会掉线；TTL 是从最后一次请求开始算的，不是从登录那一刻算
> - `afterCompletion` 里 `UserHolder.removeUser()`
>
> **第二层 `LoginInterceptor`，order=1，只拦 `/voucher-orders/**`**：
> - 逻辑极简：看 ThreadLocal 里有没有用户，没有就 `response.setStatus(401)` 并 `return false`
> - 它**不查 Redis**，纯复用第一层的结果，零额外开销
>
> **为什么要拆两个？** 因为"刷新 Token"和"强制登录"是两个正交的需求。
> 如果合成一个，就要么在所有路径上都强制登录（把商品浏览也拦了），
> 要么在需要登录的接口上重复写 Redis 查询逻辑。拆开之后：
> 刷新逻辑全局生效一次，登录校验按 path 配置，需要登录的接口加一行 path 就行，
> 职责清晰且没有重复 IO。

### Q2.4 ThreadLocal 有什么坑？你怎么处理的？🔴

**标准答案：**
> 两个坑，我都处理了：
> 1. **内存泄漏**：Tomcat 的线程是线程池复用的，请求结束后 ThreadLocal 里的 UserDTO
>    还挂在线程的 ThreadLocalMap 上，key 是弱引用会被 GC，但 **value 是强引用不会回收**，
>    线程长期存活就会导致内存泄漏，而且下一个请求可能读到上一个用户的脏数据。
>    → 我在 `RefreshTokenInterceptor.afterCompletion()` 里显式 `UserHolder.removeUser()`，
>    这是一定会执行的（即使 Controller 抛异常）。
> 2. **异步场景丢失**：ThreadLocal 绑定在当前线程，如果业务里用了 `@Async`、
>    线程池或者新开线程，子线程读不到用户信息。
>    → 我这个项目里秒杀下单是同步取 userId 的，异步落库的消息体里显式带上了 userId，
>    不依赖 ThreadLocal。如果要做线程池透传，可以用阿里 TTL（TransmittableThreadLocal）。
>
> 另外还有一个细节：拦截器里我做了 **id 字段合法性校验**，旧版本 token 的 Hash 里可能没有 id，
> 这时候我会主动 `delete` 这个 key 并不放行，避免脏 token 被误认为是有效登录。

---

## 3. 高并发秒杀 🔴🔴🔴

> 这是面试官一定会深挖的模块，务必把整条链路背熟。

### Q3.1 先讲一下秒杀的完整链路 🔴

**标准答案（建议边画边说）：**

```
① 用户请求 → Gateway(10010)：路由转发 + Sentinel 热点参数限流
        ↓
② voucher-service：RefreshTokenInterceptor 解析 Token → ThreadLocal
                    LoginInterceptor 拦 /voucher-orders/** 强制登录
        ↓
③ seckillVoucher()：查 DB 校验秒杀时间窗（begin/end）
                     库存预热到 Redis（首次）
                     RedisIdWorker 生成订单 ID
        ↓
④ 执行 Lua 脚本（原子）：
    判库存 → 判重复下单 → 扣库存 → SADD 用户 → XADD 到 Redis Stream
        ↓  返回 0 成功 / 1 库存不足 / 2 重复下单
⑤ 同步返回 orderId 给用户（用户立即看到"抢购成功"）
        ↓
⑥ RedisStreamDispatcher（独立线程，XREADGROUP）
    把 Stream 消息转发到 RabbitMQ（voucher.direct / seckill）
        ↓
⑦ VoucherMqListener 消费 seckill.queue（手动 ACK）
    → deductStock()：UPDATE ... SET stock=stock-1 WHERE voucher_id=? AND stock>0
    → INSERT voucher_order
        ↓
⑧ 失败 → basicNack(requeue=false) → 死信交换机 → dlx.queue → 落 tb_dead_letter 表
```

关键点：**第 ④ 步是同步的、原子的、极快的；第 ⑥⑦ 步是异步的**。
用户感受到的响应时间只到 ④，落库的慢操作全部甩到后台。

### Q3.2 怎么防止超卖？🔴

**标准答案：多层防护，我做了三层：**

> **第一层：Redis Lua 脚本原子扣减（主要防线）**
> ```lua
> if (tonumber(redis.call('get', stockKey) or 0) <= 0) then
>     return 1  -- 库存不足
> end
> if (redis.call('sismember', orderKey, userId) == 1) then
>     return 2  -- 重复下单
> end
> redis.call('incrby', stockKey, -1)
> redis.call('sadd', orderKey, userId)
> redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId,
>            'id', orderId, 'type', '1')
> return 0
> ```
> 为什么必须用 Lua？因为"查库存 → 判断 → 扣减"是**三步操作**，
> 如果拆成三条 Redis 命令，高并发下会出现：A 查到库存=1，B 也查到库存=1，
> 都判断通过，都去扣减，结果库存变成 -1，超卖。
> **Lua 脚本在 Redis 里是单线程原子执行的**，整个脚本执行期间不会有其它命令插进来，
> 等价于给这几步加了锁，但没有加锁的开销和超时风险。

> **第二层：数据库条件更新（兜底防线）**
> ```java
> update().setSql("stock = stock - 1")
>         .eq("voucher_id", voucherId)
>         .gt("stock", 0)     // ← 关键
>         .update();
> ```
> 生成的 SQL 是 `UPDATE seckill_voucher SET stock = stock - 1 WHERE voucher_id = ? AND stock > 0`。
> 即使 Redis 层被击穿（比如缓存被清、Lua 逻辑出 bug），这条 SQL 靠**行锁 + where 条件**
> 也能保证库存不会被扣成负数——`stock > 0` 不满足时 `affected rows = 0`，直接判定失败。
> 这是典型的**乐观锁思想**，不需要显式加锁。

> **第三层：一人一单的 Set 判重**
> `seckill:order:{voucherId}` 是一个 Set，存已购买的用户 ID，
> Lua 里 `sismember` 判断，保证同一个用户只能抢一次。

**追问：为什么不用分布式锁（Redisson）？**
> 因为秒杀的核心操作是"内存里的数值判断 + 扣减"，这个粒度 Lua 脚本就够了，
> 而且 Lua 在 Redis 内部执行，**比"加锁 → 操作 → 释放锁"少两次网络往返**，
> 也没有锁超时、锁续期、锁误释放这些复杂度。
> 分布式锁更适合跨多资源、多步骤的长事务场景。
> 当然如果业务变复杂（比如要同时扣库存 + 扣积分 + 写流水），我会考虑引入 Redisson。

### Q3.3 为什么用 Redis Stream 做 Outbox，而不是直接发 MQ？🔴

**标准答案（这是区别于培训班项目的亮点，一定要讲清楚）：**

> 直接发 MQ 有个致命问题：**Lua 脚本里没法发 RabbitMQ 消息**。
> 如果我在 Lua 执行成功后、回到 Java 再发 MQ，这中间进程可能宕机——
> 库存已经扣了，但消息没发出去，这笔订单就永久丢失了，用户付了钱却查不到券。
>
> 所以我把**"记录意图"这一步放进 Lua 脚本里**：扣库存的同时 `XADD stream.orders`，
> 这样"扣库存"和"写下待办"是原子的，要么都成功要么都失败。
> 然后 `RedisStreamDispatcher` 独立线程慢慢把 Stream 消息可靠地转发到 RabbitMQ。
>
> 这就是 **Outbox（发件箱）模式**的经典思路，只不过我用 Redis Stream 当发件箱而不是数据库表。

**Dispatcher 的可靠性设计：**
> ```java
> stringRedisTemplate.opsForStream().read(
>     Consumer.from(GROUP_NAME, CONSUMER_NAME),
>     StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
>     StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));
> ```
> 1. 用**消费组**（`group1` / `consumer1`）而不是普通 XREAD，因为消费组会记录消费位点，
>    服务重启后能从上次位置继续，不会丢消息也不会重复消费。
> 2. `BLOCK 2s` 长轮询，避免空转烧 CPU；没有新消息时不是直接 continue，
>    而是**去处理 Pending List**：
>    ```java
>    StreamOffset.create(STREAM_KEY, ReadOffset.from("0"))  // 从 0 读 = 读未 ACK 的
>    ```
>    这能捞回"上一轮发给 MQ 了但没来得及 ACK"的消息，重新转发，保证 at-least-once。
> 3. 多实例部署时消费组天然支持负载均衡（不同 consumer 分摊消息）。

### Q3.4 MQ 消费失败怎么办？消息会丢吗？🔴

**标准答案：**
> 消费端我做了**手动 ACK + 死信队列 + 死信落库**三级处理：
>
> ```java
> try {
>     voucherOrderService.createVoucherOrder(dto);
>     channel.basicAck(deliveryTag, false);          // 成功确认
> } catch (DataIntegrityViolationException e) {
>     channel.basicAck(deliveryTag, false);          // 主键冲突 = 已处理过，幂等放行
> } catch (Exception e) {
>     channel.basicNack(deliveryTag, false, false);  // requeue=false → 进死信
> }
> ```
>
> - **成功**：`basicAck`
> - **主键重复**：这是消息重复投递导致的，说明订单已经建过了，直接 ACK 丢弃（幂等）
> - **真失败**：`basicNack(deliveryTag, false, false)`，第三个参数 `requeue=false`，
>   消息不会回原队列（避免无限循环打满队列），而是走队列上配的
>   `x-dead-letter-exchange: dlx.direct` 进死信队列
> - **死信监听**：另一个 `@RabbitListener` 监听 `dlx.queue`，把消息内容 JSON 化
>   存进 `tb_dead_letter` 表（含 messageId、exchange、queueName、content、reason、status），
>   status=0 表示未处理，供人工介入或后续自动补偿
>
> 消息本身不会丢：交换机、队列、消息都是持久化的（`new Queue(name, true, false, false, args)`，
> durable=true），MQ 重启也不丢。

### Q3.5 ⚠️ 这个方案有没有不一致的地方？你怎么改进？

**这是最能体现深度的问题，要主动、诚实地回答：**

> 有的，主要有三个不一致窗口，我都清楚：
>
> **1）Redis 扣了库存，DB 还没扣（正常的异步延迟）**
> 这是设计上接受的最终一致。用户看到"抢购成功"时订单已经进 Stream+MQ，
> 正常情况下几百毫秒内落库。极端情况（MQ 挂了）靠死信表兜底。
>
> **2）Lua 成功但订单最终落库失败 → 少卖**
> 库存被扣了，用户没拿到券。这笔会进死信表，我目前的方案是**人工介入**。
> 改进方向：加一个**库存回滚**机制——消费失败超过 N 次后，
> 反向 `INCRBY seckill:stock:{id} 1` 并 `SREM seckill:order:{id} userId`，
> 把库存还回去。这样虽然用户抢不到，但至少不会造成库存凭空消失。
>
> **3）一人一单只靠 Redis Set，DB 层没有唯一索引**
> 我查过 `voucher_order` 表，`KEY idx_user_id`、`KEY idx_voucher_id`，
> **没有 `UNIQUE KEY (user_id, voucher_id)`**。如果 Redis Set 被清（比如内存淘汰、
> 运维误删），同一用户就能重复抢到。
> 改进：**一定要加这唯一索引**，让数据库做最后一道防线，
> 消费端捕获 `DuplicateKeyException` 直接 ACK（我在商城订单模块就是这么做的）。
>
> **4）库存预热的并发细节**
> ```java
> if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey))) {
>     stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(voucher.getStock()));
> }
> ```
> `hasKey` + `set` 不是原子的，但这个场景写的是同一个静态值，重复写无害。
> 真正的风险是：如果 key 因为某些原因被删了，会**重新预热成 DB 里的初始库存**，
> 可能把已卖出的库存"还回去"造成超卖。
> 改进：预热动作应该放在**后台创建秒杀券时**同步做，而不是放在秒杀请求的路径上；
> 并且用 `SETNX` 而不是 `hasKey + SET`。

### Q3.6 秒杀接口还能怎么优化？（开放题）

**标准答案（分层递进）：**
> **已在做的**：Lua 原子扣减、异步化削峰、库存预热到 Redis、热点参数限流。
>
> **可以继续做的**：
> 1. **前端限流**：按钮置灰、倒计时、防重复提交，把 80% 的无效请求挡在浏览器。
> 2. **网关层**：Sentinel 按接口维度限流 + 令牌桶；对同一 IP / 同一 userId 做更严格的频率限制
>    （我现在只对带 token 的请求限 100 QPS，无 token 的请求是放行的）。
> 3. **库存分段**：把 1000 件库存拆成 `stock:1` ~ `stock:10` 十个 key，
>    请求随机打到某个分段，把单 key 热点打散（Redis 单分片热点问题）。
> 4. **内存标记**：库存扣完后在 JVM 本地放个 boolean 标记，后续请求直接在内存返回"已抢完"，
>    连 Redis 都不用查了（类似 Sentinel 的"快速失败"）。
> 5. **Redis 集群**：单实例 Redis 扛不住时做分片，秒杀 key 单独分到一个分片。
> 6. **答题/验证码**：真正的秒杀系统会加答题或滑块，把请求在时间上摊平。

---

## 4. 分布式 ID

### Q4.1 你的订单 ID 怎么生成的？讲讲原理

**标准答案：**
> 我用的是 **Redis 自增序列 + 时间戳位拼接**，不是雪花算法。实现在 `RedisIdWorker`：
>
> ```java
> private static final long BEGIN_TIMESTAMP = 1640995200L;  // 2022-01-01 00:00:00 UTC
> private static final int COUNT_BITS = 32;
>
> public long nextId(String keyPrefix) {
>     long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;
>     String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
>     long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
>     return timestamp << COUNT_BITS | count;
> }
> ```
>
> **位结构**（64 位 long）：
> ```
> | 符号位 1 | 时间戳 31 位 | 序列号 32 位 |
> ```
> - 低 32 位是当天自增序列号，Redis key 是 `icr:order:2026:09:17`，**按天重置**
> - 高 31 位是相对 2022-01-01 的秒数
> - 拼接用 `<<` 和 `|`（按位或，因为低 32 位全 0，或运算等价于加法但更快）

**几个数字要能算出来：**
> - 符号位固定 0，所以 ID 恒为正数
> - 时间戳 31 位 → 最大 2³¹ 秒 ≈ 68 年 → 可以用到 **2090 年**
> - 序列号 32 位 → 单业务单日最多 **42.9 亿**个不重复
> - 商城订单号另有一套：`yyyyMMdd` + Redis 当日自增 6 位，并有 `uk_order_no` 唯一索引兜底

**优点：**
> - **趋势递增**：同一秒内序列号递增，跨秒时间戳变大，整体单调递增，
>   用作数据库主键时对 B+ 树友好，不会像 UUID 那样造成页分裂
> - **无时钟回拨问题**：时间戳取自 Redis 服务端吗？不是，取自本机——
>   但因为同一秒内靠 Redis 序列号区分，即使两台机器时钟有微小偏差也不会冲突
>   （序列号全局唯一就够了），这比雪花算法的时钟回拨问题好处理
> - **ID 自带时间信息**，方便按天分库分表和归档（key 里就带日期）

### Q4.2 和雪花算法（Snowflake）比有什么区别？为什么不用雪花？

**标准答案：**
> 雪花是 `1位符号 + 41位时间戳 + 10位机器ID + 12位序列号`：
>
> | 维度 | Redis 自增方案 | 雪花算法 |
> |------|---------------|---------|
> | 依赖 | 依赖 Redis 可用性 | 依赖机器时钟 |
> | 时钟回拨 | 不敏感 | 会生成重复 ID，需要额外处理 |
> | 机器 ID | 不需要分配 | 10 位机器位需要人工分配/注册中心协调 |
> | 性能 | 每次一次 Redis 网络往返 | 本地生成，纯内存，快得多 |
> | QPS 上限 | 受 Redis 限制 | 单机每毫秒 4096 个 |
> | 序列号粒度 | 按天，会重置 | 按毫秒，每毫秒重置 |
>
> 我选 Redis 方案主要是**简单可控、不需要管理工作节点 ID**，
> 也天然规避了时钟回拨这个经典坑。
>
> **但如果要上生产高并发，我会改成号段模式（Leaf-segment）**：
> 每次从 DB/Redis 批量取 1000 个 ID 缓存在本地内存里用，用完再取，
> 这样把网络 IO 降低 1000 倍，同时保留趋势递增和无回拨的优点。
> 美团 Leaf、滴滴 TinyID 都是这个思路。

### Q4.3 Redis 挂了或者重启，ID 会重复吗？

**标准答案：**
> **理论上会，需要看持久化配置。**
> `INCR` 的结果存在内存里，如果 Redis 进程挂掉且 **AOF 没来得及刷盘**，
> 重启后 `icr:order:2026:09:17` 的值会回退到上次持久化的值，继续自增就可能生成重复 ID。
>
> Redis 默认 `appendfsync everysec`，最坏丢失 1 秒的数据，也就是可能重复生成 1 秒内数量的 ID。
>
> **规避方案**：
> 1. 依赖数据库主键唯一约束兜底——`voucher_order` 的主键是 `id`，
>    真重复了 INSERT 会报 `DuplicateKeyException`，我的消费端已经捕获了这个异常做幂等处理，
>    不会造成脏数据，只是这笔订单失败
> 2. 生产环境可以：AOF 改 `appendfsync always`（性能换可靠），
>    或者改用号段模式（号段用完才回 DB，Redis 抖动影响面小），
>    或者直接用数据库 `REPLACE INTO` 发号（美团 Leaf 的号段方案）

---

## 5. 缓存设计 🔴

### Q5.1 讲讲缓存穿透、击穿、雪崩，你是怎么解决的？🔴

**标准答案（概念 + 我的实现）：**

**缓存穿透**——查询根本不存在的数据，缓存永远不命中，全部打到 DB。
> 解决：
> 1. **缓存空值**：查不到时也写一个空标记进 Redis，TTL 短一些。
>    我定义了 `CACHE_NULL_TTL = 2L`（2 分钟），避免空值长期占位。
> 2. **布隆过滤器**：把所有存在的 ID 预先灌进布隆过滤器，请求先过过滤器，
>    不存在直接返回。这是最彻底的方案，适合 ID 固定、数据量大的场景。
> 3. **参数校验**：接口层就拦截非法 ID（负数、超长、非数字）。
>
> ⚠️ 我这项目里有个特殊的坑，我专门处理了：
> ```java
> /** 空列表不写缓存、命中时视为脏数据需回源，避免「先空库访问」挡 30 分钟无数据。 */
> private static boolean isEmptyScrollResult(Object o) { ... }
> ```
> 如果数据库刚初始化、食材表是空的，第一次查询返回空列表，
> 按常规逻辑会把"空"缓存 30 分钟——结果后面导入了数据，
> 用户却要等 30 分钟才能看到。**所以我做了：空列表不写缓存，
> 且命中空列表时视为脏数据，主动删除 key 并回源。**

**缓存击穿**——某个热点 key 刚好过期，瞬间大量请求全部打到 DB。
> 解决：我用**逻辑过期 + 互斥锁重建**（详见 Q5.2）。
> 另外还有一种思路是**永不过期 + 异步更新**（我的"今日推荐"就是这种）。

**缓存雪崩**——大批 key 在同一时刻集体失效，或者 Redis 直接宕机。
> 解决：
> 1. **TTL 加随机值**：`基础TTL + Random(0, 5分钟)`，让过期时间错开
> 2. **逻辑过期方案天然免疫**：我的热点缓存根本不设物理 TTL（详见 Q5.2），
>    不存在"集体失效"这一刻
> 3. **多级缓存**：本地 Caffeine + Redis，Redis 挂了本地还能顶一阵
>    （我在实习项目里用过 Caffeine，这个项目是纯 Redis）
> 4. **Redis 高可用**：哨兵/集群 + 持久化

### Q5.2 逻辑过期是怎么实现的？为什么不用互斥锁方案？🔴

**标准答案：**
> 核心在 `CacheClient.queryWithLogicalExpire()`：
>
> **数据结构**：value 里不直接存业务对象，而是包一层 `RedisData`：
> ```java
> RedisData { Object data; LocalDateTime expireTime; }
> ```
> 写入时 `expireTime = now + 30分钟`，但 **key 本身不设 TTL**（永不物理过期）。
>
> **读取流程**：
> ```
> 1. 查 Redis
> 2. 没命中 → 直接查 DB 并重建缓存，返回
> 3. 命中 → 反序列化出 RedisData
> 4. expireTime > now（未过期）→ 直接返回 data
> 5. 已过期 → tryLock(lockKey)
>      ├─ 抢到锁 → 提交任务到线程池异步查 DB → 重建缓存 → finally 释放锁
>      └─ 没抢到 → 什么都不做
> 6. 无论是否抢到锁，都立即返回旧的 data（不阻塞）
> ```
>
> 锁的实现就是最简单的 `setIfAbsent(key, "1", 10, SECONDS)`，带 10 秒 TTL 防死锁。
>
> **重建线程池的参数我是有考虑的**：
> ```java
> new ThreadPoolExecutor(10, 10, 0L, MILLISECONDS,
>         new LinkedBlockingQueue<>(100),        // 有界队列防 OOM
>         Executors.defaultThreadFactory(),
>         new ThreadPoolExecutor.AbortPolicy()); // 拒绝策略
> ```
> 用**有界队列 100** 而不是默认的 `Integer.MAX_VALUE`，防止缓存集中失效时
> 任务无限堆积把内存撑爆——这是很多人写线程池会忽略的点。
>
> **为什么选逻辑过期而不是互斥锁？**
> | | 逻辑过期 | 互斥锁（Cache Aside + 锁重建） |
> |---|---------|---------------------------|
> | 一致性 | 弱，可能返回过期数据 | 强，拿到的一定是新数据 |
> | 可用性 | 高，永不阻塞、永不等待 | 低，拿不到锁的线程要休眠重试 |
> | 复杂度 | 高，要维护 expireTime 字段 | 低 |
> | 适用 | 热点高 QPS、允许短暂陈旧 | 一致性要求高、QPS 不高 |
>
> 食材首页属于**读极多、更新极少、允许几秒钟陈旧**的数据，
> 所以选逻辑过期——哪怕重建要 100ms，这 100ms 内进来的几万个请求
> 都是直接拿到旧值返回，**没有一个线程被阻塞**，这是秒杀场景最需要的性质。

### Q5.3 「今日推荐食材」的缓存是怎么做的？为什么用 rename？

**标准答案：**
> 这个是**异步预热型**缓存，思路和逻辑过期不同：
>
> 1. **定时任务**（`RecommendTask`）定时调用 `refreshRecommend()` 重新随机挑 8 条食材
> 2. **写入时用"临时 key + rename"**：
> ```java
> String tmpKey = key + ":tmp:" + System.currentTimeMillis();
> stringRedisTemplate.opsForValue().set(tmpKey, json, 24, TimeUnit.HOURS);
> stringRedisTemplate.rename(tmpKey, key);   // 原子操作
> ```
> **为什么这么绕？** 因为 `SET` 一个大 JSON 不是瞬时的，如果直接覆盖正式 key，
> 在写入过程中（哪怕只有几毫秒）其它线程读到的可能是**写了一半的值**，
> 或者更糟——如果写入中途异常，正式 key 就脏了。
> 先写临时 key，写完整了再用 `RENAME` **原子地**替换，
> 保证任何时刻读到的都是完整的旧值或完整的新值，**不存在中间态**。
> 这本质上是数据库"影子表"的思路。
>
> 3. **兜底**：缓存读不到时（首次启动、任务还没跑）实时查一次并回填，不会返回空。
>
> **随机取数据的优化点**（可以提）：
> ```java
> List<Ingredient> all = list();
> Collections.shuffle(all);
> return all.subList(0, size);
> ```
> 我没用 `ORDER BY RAND()`，因为那是**全表扫描 + 全表排序**，数据量大时是灾难；
> 我改成全量取出在内存 shuffle。
> 但诚实说，食材表数据量小（几百条）这样没问题，
> 真到几十万条还得改成"随机 ID 采样"或者"取 max(id) 随机偏移"。

### Q5.4 缓存和数据库的一致性怎么保证？

**标准答案：**
> 我用的是业界最主流的 **Cache Aside Pattern（旁路缓存）**：
> - **读**：先读缓存，命中直接返回；没命中读 DB 再写回缓存
> - **写**：先更新 DB，**再删除缓存**（不是更新缓存）
>
> **为什么是删除而不是更新缓存？**
> 因为很多缓存值是经过复杂计算/多表聚合的（比如食材首页是分页对象），
> 每次更新都重算一遍很浪费；而且如果频繁更新但很少读，这些计算全白做了。
> 删除是"懒加载"思路——需要的时候才重建。
>
> 我的后台管理模块（`/admin/**` 的增删改）就是这么做的，改完食材/商家/商品会主动清理对应缓存。
>
> **这个方案的已知问题**（主动暴露）：
> 1. **并发读写的不一致窗口**：线程 A 读缓存未命中 → 查 DB 得到旧值；
>    此时线程 B 更新 DB 并删缓存；然后 A 才把旧值写进缓存 → 缓存里是脏数据。
>    → 这个概率很低（需要"读操作比写操作慢"且刚好错过），
>    真要严格保证可以加**延迟双删**（删缓存 → 更新 DB → sleep 一小会儿 → 再删一次），
>    或者用 **Canal 订阅 binlog 异步删缓存**（彻底解耦业务代码）。
> 2. **删缓存失败**：DB 更新成功但缓存没删掉 → 一直脏。
>    → 可以用 MQ 重试删除，或者依赖 TTL 兜底。

---

## 6. Elasticsearch 搜索与降级

### Q6.1 ES 搜索是怎么做的？为什么要用 ES？

**标准答案：**
> 为什么不用 MySQL `LIKE`：
> - `LIKE '%关键词%'` **用不上索引**，必然全表扫描，数据量大就完蛋
> - 无法做**相关度排序**（谁更匹配排前面）
> - 无法做**高亮**、分词、同义词、拼音搜索
>
> ES 靠**倒排索引**——把每个词映射到包含它的文档列表，查询是 O(1) 定位，
> 天生适合全文检索。
>
> 我的实现：
> ```java
> NativeSearchQuery query = new NativeSearchQueryBuilder()
>         .withQuery(QueryBuilders.multiMatchQuery(key, "name", "description")
>                                 .operator(Operator.AND))
>         .withPageable(PageRequest.of(0, 20))
>         .build();
> SearchHits<IngredientDoc> hits = elasticsearchRestTemplate.search(query, IngredientDoc.class);
> ```
> - `multiMatchQuery` 在 `name` 和 `description` 两个字段里搜
> - `Operator.AND` 表示**分词后的每个词都要匹配**（搜"番茄炒蛋"，
>   必须同时含"番茄"和"炒蛋"，只含"番茄"的不算），提高精准度
> - ⚠️ **简历写的是 IK 分词器，但代码里实际是 `analyzer = "standard"`**
>   （`IngredientDoc` 上的注解）。standard 对中文是**单字切分**，不是真正的词。
>   这是简历与代码不一致的地方，**面试前必须二选一处理掉**：
>   - **改代码**（推荐）：给 ES 容器装 IK 插件，再把注解改成
>     `@Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")`
>     注意 docker-compose 里 ES 的 plugins 挂载是注释掉的，要先打开
>     `- ./es/plugins:/usr/share/elasticsearch/plugins` 并把插件放进去，
>     否则索引创建会因 analyzer 不存在而失败。
>   - **改简历**：把"集成 Elasticsearch + IK 分词器"改成
>     "集成 Elasticsearch，中文按字切分检索，支持 ES 超时自动降级"。
>   > 诚实讲法：「IK 需要额外装插件，我为降低部署门槛用了 standard 单字切分配合 AND 语义，
>   >   在食材这种短文本场景下召回率反而更高；生产环境数据量上来后会切到 IK。」
> - 返回前 20 条

### Q6.2 ES 挂了怎么办？降级策略怎么实现的？🔴

**标准答案：**
> 我做了**两层降级**，保证 ES 不是系统的单点：
>
> **第一层：Bean 层面的可选装配**
> ```java
> @Autowired(required = false)
> private ElasticsearchRestTemplate elasticsearchRestTemplate;
> ```
> 配合配置项 `ITEM_ELASTICSEARCH_ENABLED`，如果 ES 没启用，`required=false`
> 让这个 Bean 为 null，代码里判空直接走 MySQL。
> 这样**启动时 ES 不存在也不会报错**，项目能正常起来。
> 这对"4GB 内存服务器跑不动 ES"的场景很有用——把这个开关关掉就能部署。
>
> **第二层：运行时的异常兜底**
> ```java
> try {
>     // ES 查询
> } catch (Exception e) {
>     logger.error("ES搜索异常或超时，触发MySQL兜底查询, keyword: {}", key, e);
>     return searchFromDb(key);
> }
> ```
> ES 超时、连接失败、集群健康检查不过，任何异常都会降级到 MySQL 模糊查询。
>
> **MySQL 兜底的实现思路**也值得一提：
> ```java
> char[] chars = key.toCharArray();
> queryWrapper.and(w -> {
>     for (char c : chars) { w.like(Ingredient::getName, String.valueOf(c)); }
> }).or(w -> {
>     for (char c : chars) { w.like(Ingredient::getDescription, String.valueOf(c)); }
> }).last("LIMIT 20");
> ```
> 我把关键词**拆成单字**，要求每个字都在 name 中出现（OR description 中每个字都出现），
> 再 `LIMIT 20` 截断。这样搜"生米"时能匹配到"大米(生)"——
> 因为"生"和"米"都在里面。这是在**没有分词能力时模拟 AND 语义**的土办法。
> ⚠️ 性能上它是全表扫描——**这是改造前的实现，现已升级为 ngram 全文索引，详见 Q6.3**。
>
> **同步机制**：`EsSyncTask` 定时任务全量 `saveAll`。
> ⚠️ **改进点**：全量同步在数据量大了之后会有性能问题，
> 应该改成**增量同步**——用 Canal 监听 binlog，或者业务更新时发 MQ 事件让 ES 增量更新。

### Q6.3 🔴 你说降级到 MySQL 模糊匹配，但 LIKE '%x%' 性能很差，怎么解决？

> **这是最可能被追问的问题，也是我实际改造过的地方。回答时先承认、再给方案、最后说取舍。**

**先承认问题（诚实建立信任）：**
> 是的，最初的降级实现确实很粗糙。我当时是把关键词拆成单字拼
> `WHERE (name LIKE '%番%' AND name LIKE '%茄%') OR (description LIKE ...)`，
> 语义上想模拟分词后的 AND，但性能上是灾难：
> 1. **索引完全失效**：`LIKE '%x%'` 是前导通配符，B+ 树无法定位起始位置，只能全表扫描
> 2. **无相关度排序**：谁更匹配排前面完全做不到
> 3. **语义仍不准**：单字 AND 允许乱序，"番X茄"这种也能匹配上
>
> 而且更严重的是**降级触发机制**也有问题——原来是"try ES，超时才 catch 降级"，
> ES 真正故障期间**每个请求都要卡满超时**才能降级，
> Tomcat 线程池很快被占满，从"搜索变慢"演变成"整个服务不可用"，这是典型的故障放大。

**然后给出改造方案（分三层）：**

> **第一层：治本——别让它轻易降级**
> - 给 ES 查询显式加超时（原来是裸奔的）：`.withTimeout(TimeValue.timeValueMillis(500))`
> - 加**熔断器**：滑动窗口统计失败率，10 秒窗口内 ≥10 次调用且失败率 > 50% 就熔断 30 秒。
>   熔断期间**直接短路走降级，一次都不再访问 ES**，
>   冷却结束后进 HALF_OPEN 放 3 个探测请求，成功则关闭熔断。
>   标准三态机（CLOSED / OPEN / HALF_OPEN），和 Resilience4j、Sentinel 一致。

> **第二层：降级实现升级——MySQL ngram 全文索引**
> MySQL 5.7.6+ 内置了 **ngram 全文解析器**，专为中日韩设计，建真正的倒排索引：
> ```sql
> ALTER TABLE ingredient ADD FULLTEXT INDEX ft_name_desc (name, description) WITH PARSER ngram;
> ```
> 查询从 `LIKE` 换成：
> ```sql
> SELECT ..., MATCH(name, description) AGAINST(? IN BOOLEAN MODE) AS score
> FROM ingredient
> WHERE deleted = 0 AND MATCH(name, description) AGAINST(? IN BOOLEAN MODE)
> ORDER BY score DESC LIMIT 20;
> ```
> | | LIKE '%x%' | MATCH ... AGAINST |
> |---|---|---|
> | 索引 | **失效**，全表扫描 O(n) | 走 FULLTEXT 倒排索引，定位 posting list |
> | 排序 | 无 | MySQL 按 TF-IDF 算 score，可 ORDER BY |
> | 中文 | 只能子串匹配 | ngram 分词，语义接近真分词 |
>
> **这里有个关键设计点**：服务端用 `ngram_token_size=2` 把文档切成二元 token
> （"番茄炒蛋" → "番茄" "茄炒" "炒蛋"），
> 那**查询串也必须用同样的切分规则**才能命中对等的 token。
> 所以我在应用层做了相同的 bigram 切分并用 `+` 连接成 AND 语义：
> ```
> "番茄炒蛋" → "+番茄 +茄炒 +炒蛋"
> ```
> 这个 AND 语义恰好等价于"必须包含完整的番茄炒蛋"，
> 正好和 ES 侧的 `Operator.AND` 对齐，**降级后搜索体验不会明显劣化**。
> 同时清洗掉 `+ - * ~ < > ( ) "` 这些 BOOLEAN MODE 操作符，防止用户构造特殊查询串。

> **第三层：架构升级——策略模式责任链**
> 把检索方式抽象成 `IngredientSearchStrategy`（`name()` / `order()` / `available()` / `search()`），
> 用责任链按优先级串起来：
> ```
> ES(10) → MySQL-FULLTEXT(20) → MySQL-LIKE(30)
> ```
> 每个策略自己声明"当前是否可用"（ES 未装配 or 被熔断 → false；
> 关键词短于 ngram token size → false），链上挑第一个可用的执行，
> 抛异常自动落到下一级。**业务代码只调 `chain.search(key)`，完全不感知降级过程。**
>
> 两个工程细节：
> - **索引缺失自动降级**：运维没跑 migration 时 `MATCH` 会报错。
>   我捕获后把策略**永久置为不可用**（`volatile disabled = true`），
>   后续直接走 LIKE，不会每次都抛异常再降级。
> - **降级埋点**：统计各策略成功/失败次数与耗时。
>   降级是"静默失败"，没埋点的话线上很可能长期走降级而无人知晓。

**最后说效果和下一步：**
> 改造后 ES 不可用时不再是全表扫描，而是走倒排索引 + 相关度排序；
> ES 故障期间也不会拖垮线程池。
> 单字关键词（"米"、"菜"）因 `ngram_token_size=2` 命中不了全文索引，
> 仍走 LIKE，但单字检索语义简单、占比低，可以接受。
> 再往后如果数据量继续增长，下一步引入**本地 Lucene 内存索引**——
> 食材只有几百上千条，完全可以全量放 JVM 内存建索引，
> 降级几乎无损（无网络开销，性能和 ES 相当）。

### Q6.4 MySQL ngram 全文索引有什么坑？

> **1）`ngram_token_size` 是只读变量**
> 只能在启动时由配置文件指定，**运行时 `SET GLOBAL` 无效，必须重启 MySQL**。
> Docker 下就是挂 `conf.d/ngram.cnf` 并重建容器。
>
> **2）`innodb_ft_min_token_size` 对它不生效**
> 很多人以为要同步改这个参数（默认 3），其实 MySQL 官方文档明确说了
> ngram parser 的 token 长度固定由 `ngram_token_size` 决定，
> 那两个变量**不适用于 ngram**。这是常见误解。
>
> **3）token_size=2 时单字搜不到**
> 索引里最小 token 是 2 个字，搜"米"命中不了。
> 我在应用层做**长度路由**：关键词短于 token_size 就跳过全文策略走 LIKE。
> 设成 1 能搜单字，但索引体积膨胀且精度大幅下降，不划算。
>
> **4）`MATCH()` 的列必须和建索引时完全一致**（顺序、数量都要一致），
> 否则报 `Can't find FULLTEXT index matching the column list`。

### Q6.5 为什么一定要熔断？catch 降级不行吗？

> catch 降级只能保证"最终有结果"，**保证不了止损**。
>
> ES 挂了之后，假设超时 3 秒，那每个搜索请求都要先占一个 Tomcat 线程 3 秒才拿到降级结果。
> QPS 100 的话一秒就堆积 300 个线程在等——Tomcat 默认线程池才 200，
> 很快全部占满，之后**连不需要 ES 的接口（商品详情、购物车）也全部超时**，
> 一个组件故障扩散成整个服务不可用，这就是雪崩。
>
> 熔断器的价值是**快速失败**：确认下游不可靠后，后续请求在**微秒级**返回降级结果，
> 一个线程都不占；同时定期放探测请求，下游恢复后自动关闭。
> 本质是"用一小段时间的可用性下降，换取系统不被拖垮"。
>
> 参数选择也有讲究：
> - **最小调用数（10）**：样本太少不判断，避免"1 次失败就熔断"的误判
> - **失败率阈值（50%）**：而非"连续失败 N 次"，因为低 QPS 时连续失败可能是偶发
> - **冷却期（30 秒）**：太短频繁探测，太长恢复慢
> - **半开放行数（3）**：放少量请求试探，不是一下全量打过去

### Q6.6 LIKE / MySQL FULLTEXT / Elasticsearch 怎么选型？

> | 维度 | LIKE | MySQL FULLTEXT | Elasticsearch |
> |------|------|---------------|---------------|
> | 索引 | 全表扫描 | 倒排索引 | 倒排索引（分布式） |
> | 数据量 | 几千条以内 | 百万级 | 亿级 |
> | 分词 | 无 | ngram（粗粒度） | IK/standard 专业分词 |
> | 相关度排序 | 无 | 有（TF-IDF） | 有（BM25，更准） |
> | 高亮 | 无 | 需自己切词 | 原生支持 |
> | 扩展性 | 无 | 受单机限制 | 水平分片 |
> | 运维成本 | 0 | 0（复用 MySQL） | 高（独立集群） |
>
> 我的选型逻辑：
> - **主路径用 ES**：数据量大、要分词质量、要高亮和相关度
> - **降级用 MySQL FULLTEXT**：已经在用 MySQL，零额外组件就能拿到倒排索引——
>   这恰恰是降级方案最重要的性质：**不能引入新的依赖**，否则降级方案自己也可能挂
> - **LIKE 只做最后防线**：保证功能永不 500，质量差一点无所谓
>
> 如果数据量特别小（几百条字典数据），其实**连 ES 都不用上**，
> 直接 MySQL FULLTEXT 或本地内存索引就够了——技术选型要匹配数据规模。

### Q6.7 ES 写入后多久能搜到？（如果问到）

> ES 是近实时（NRT），默认 `refresh_interval = 1s`，
> 也就是写入后最多 1 秒才能被搜到。
> 如果要立即见效可以手动 `_refresh`，但会强制生成新 segment，
> 频繁调用严重影响性能，生产不推荐。
> 我的定时同步任务是批量的，对 1 秒延迟不敏感。

---

## 7. 游标分页

### Q7.1 为什么用游标分页？深分页有什么问题？🔴

**标准答案：**
> **深分页的问题**：
> ```sql
> SELECT * FROM ingredient ORDER BY id DESC LIMIT 1000000, 20;
> ```
> MySQL 必须**先扫描出前 1000020 行，然后丢弃前 1000000 行**，
> 只返回最后 20 行。偏移量越大，扫描的行数越多，性能线性劣化。
> 而且即使有索引，回表取完整行的代价也很大，还可能触发 filesort。
>
> **游标（Cursor）分页的原理**：
> 记住上一页最后一行的排序键值，下一页从这个值继续：
> ```java
> if (lastId != null) {
>     queryWrapper.lt(Ingredient::getId, lastId);
> }
> queryWrapper.orderByDesc(Ingredient::getId).last("limit " + limit);
> ```
> 生成 `WHERE id < ? ORDER BY id DESC LIMIT ?`。
> 因为 `id` 是主键索引，**走索引直接定位到起始位置**，取 20 行就停，
> 扫描行数和页码无关，恒定为 20 行。
>
> **返回结构** `ScrollResult { List list; Long minId; Integer size; }`，
> `minId` 就是下一页的游标，由 `list.get(size-1).getId()` 得到。

**追问：游标分页有什么局限？**
> 1. **不能跳页**：无法直接跳到第 100 页，只能"加载更多"（这正适合信息流/无限滚动场景，
>    我的食材列表就是这种模式）
> 2. **要求排序键唯一且稳定**：我用的是主键 id，天然满足。
>    如果按 `create_time` 排序，同一秒的多行会导致游标边界处漏数据或重复，
>    需要 `(create_time, id)` 联合游标。
> 3. **数据变动会导致轻微漂移**：翻页期间如果有新数据插入，可能重复看到。
>    （我的表里还有 `seq_no` 唯一键，就是为了做稳定排序用的）

**优化扩展（可以提）**：
> 还有两种常见优化：
> - **延迟关联/子查询**：`SELECT * FROM t WHERE id IN (SELECT id FROM t LIMIT 1000000, 20)`，
>   子查询只扫索引不回表，减少回表量
> - **覆盖索引**：只查索引包含的列，避免回表
> 但这两种只是缓解，根上还是要用游标分页。

### Q7.2 首页查询为什么特殊处理？

> 首页（lastId == null）的请求量远大于翻页请求，是真正的热点，
> 所以我给它套了逻辑过期缓存：
> ```java
> String cacheKey = RedisConstants.INGREDIENT_FIRSTPAGE_KEY + ":" + limit;
> ```
> 注意 **cacheKey 里带了 limit**——因为不同分页大小返回的数据不同，
> 如果共用一个 key，前端换 limit 就会拿到错误的数据。这是个容易忽略的细节。
> 翻页请求（lastId != null）组合太多、缓存命中率低，直接查库，靠主键索引已经很快了。

---

## 8. 限流与网关

### Q8.1 Sentinel 限流是怎么做的？

**标准答案：**
> 我在 Gateway 上做了一个 `GlobalFilter`，用的是 Sentinel 的**热点参数限流**：
>
> **规则定义**（`@PostConstruct` 里加载）：
> ```java
> ParamFlowRule rule = new ParamFlowRule("user_rate_limit")
>         .setParamIdx(0)            // 对第 0 个参数限流
>         .setCount(100)             // QPS 阈值 100
>         .setDurationInSec(1);      // 统计窗口 1 秒
> ```
>
> **埋点**：
> ```java
> entry = SphU.entry("user_rate_limit", EntryType.IN, 1, token);
> ```
> 把用户的 token 作为参数传进去，Sentinel 就会**按 token 维度单独统计**——
> 也就是说，"用户 A 每秒 100 次"和"用户 B 每秒 100 次"是分开算的，
> 这比"整个接口每秒 100 次"精准得多，能防止单个用户刷接口把其他用户挤掉。
>
> **限流响应**：返回 429 + JSON `{"code":429,"message":"请求过于频繁，请稍后再试！"}`
>
> **过滤器顺序** `getOrder() = -1`，保证在路由转发之前执行，被拦掉的请求不会打到下游服务。
>
> ⚠️ 我的实现里**没有 token 的请求是直接放行的**——这是考虑到游客也能浏览商品。
> 但这样就有风险：攻击者不带 token 就能绕过限流。
> 改进：对无 token 请求按 **IP 维度**限流（Sentinel 支持 `setParamIdx` 传 IP，
> 或者用 Gateway 自带的 `RequestRateLimiter` + Redis 令牌桶）。

### Q8.2 常见限流算法有哪些？

**标准答案（四个都要能说）：**
> | 算法 | 原理 | 优点 | 缺点 |
> |------|------|------|------|
> | **固定窗口计数器** | 每秒一个计数器，超过阈值拒绝 | 实现最简单 | **临界问题**：0.9s 来 100 个 + 1.1s 来 100 个，窗口内都合法，但 0.2 秒内实际来了 200 个 |
> | **滑动窗口** | 把窗口切成小格，按时间滑动统计 | 解决临界问题 | 内存占用稍大 |
> | **漏桶** | 请求进桶，桶以固定速率漏水（处理） | 输出速率恒定，平滑流量 | 无法应对突发流量，超出的直接丢弃 |
> | **令牌桶** | 桶里以固定速率生成令牌，请求拿令牌才能过 | **允许突发**（桶里攒的令牌可一次用完） | 实现稍复杂 |
>
> Sentinel 底层是**滑动窗口**（LeapArray），Gateway 的 `RequestRateLimiter`
> 默认用 Redis 实现**令牌桶**。
> 业务上令牌桶最常用，因为它既能限流又能容忍合理的突发——
> 用户点了一下按钮连发两个请求，不该被拒绝。

---

## 9. 异步下单与最终一致性（商城订单）

> 这部分简历没写但代码里实现得很完整，是**极佳的加分项**，建议主动提一句：
> "除了优惠券秒杀，购物车结算下单我也做了异步化和一致性设计。"

### Q9.1 商城下单的完整流程？

**标准答案：**
> 分**同步受理**和**异步推进**两段：
>
> **同步段（事务内，毫秒级）**：
> 1. 校验收货人信息非空
> 2. `clientToken` 幂等检查（见 Q9.2）
> 3. 解析购物车选中项，校验商品存在且上架
> 4. 如果用了券 → Feign 调 voucher-service 的 `precheck` 预校验，
>    并校验券所属商家确实在本单商品里
> 5. 生成订单号 `yyyyMMdd + Redis当日自增6位`
> 6. **事务内**：写 `trade_order`（status=4 处理中）+ 明细快照（价格按现价锁定）
>    + 状态流转日志
> 7. 事务提交后投递 MQ（**只发 orderNo**，不发完整数据）
> 8. 立即返回订单号给用户
>
> 这里的关键是：**订单先以"处理中"落库，再把耗时操作（核销券、清购物车）甩到后台**，
> 用户不用等跨服务调用。

> **异步段（MQ 消费者）**：
> 1. 幂等检查：只处理 status=4 的订单
> 2. 跨服务核销优惠券（Feign，voucher-service 内部也是幂等的）
> 3. **CAS 推进状态**：
>    ```java
>    update().eq("order_no", orderNo)
>            .eq("status", STATUS_PROCESSING)     // ← 条件更新，并发下只有一个赢
>            .set("status", STATUS_PENDING)
>            .set("pay_amount", pay);
>    ```
> 4. 清理购物车
>
> 状态机：`4 处理中 → 0 待支付 → 1 已支付 → 2 已完成 / 3 已取消 / 5 失败`

### Q9.2 怎么防止重复下单？🔴

**标准答案：两层防护：**

> **第一层：clientToken 幂等键**
> 前端每次进入结算页生成一个 `clientToken`，提交订单时带上。
> 后端先用它查一下是不是已经下过单：
> ```java
> TradeOrder existed = getOne(...eq(clientToken).in(status, PROCESSING, PENDING)...);
> if (existed != null) return Collections.singletonList(existed.getOrderNo());  // 返回原单
> ```
> 这样用户手抖连点两次、或者网络重试，只会生成一个订单。
>
> **第二层：数据库唯一索引兜底**
> ```sql
> UNIQUE KEY `uk_user_client_token` (`user_id`, `client_token`)
> ```
> 并发下两个请求同时过了第一层检查，插入时必然有一个撞唯一索引，
> 抛 `DuplicateKeyException`。我在 catch 里做了**语义区分**：
> ```java
> catch (DuplicateKeyException e) {
>     TradeOrder dup = 按 clientToken 查;
>     if (dup != null) {
>         if (状态是 处理中/待支付) → 幂等命中，返回已受理订单
>         else → 说明是"终态订单占用了幂等键"，本次不带 clientToken 重新创建
>     }
>     // 按 token 查不到 → 才是真的订单号冲突，重生成订单号再试一次
> }
> ```
> 这个分支判断很重要：**不要把"幂等键冲突"误判成"订单号冲突"去换号重试**，
> 否则会二次撞索引，甚至生成两笔订单。
>
> **幂等键的释放**：订单进入终态（失败/取消/完成）时把 `clientToken` 置为 null，
> 用户才能重新结算——这就是代码注释里说的"**在途唯一**"语义，
> 幂等键只在订单"在途"期间有效，不会永久占坑。
>
> 另外订单号本身也有 `uk_order_no` 唯一索引做最后兜底。

### Q9.3 MQ 消费失败了怎么办？重试策略？🔴

**标准答案：这是我设计得最细的一块，分三类处理：**

> **1）瞬时故障 → 延迟重试队列（30 秒 × 3 次）**
> ```java
> private boolean isTransient(Throwable t) {
>     // ConnectException / SocketTimeoutException / RetryableException
> }
> ```
> 只有网络类异常才重试（比如调券服务超时），因为是"过一会儿自己会好"的。
> 重试用**延迟队列**实现：
> ```java
> QueueBuilder.durable(RETRY_QUEUE)
>     .withArgument("x-message-ttl", 30000)                    // 停留 30 秒
>     .withArgument("x-dead-letter-exchange", ORDER_EXCHANGE)  // 到期死信回主交换机
>     .withArgument("x-dead-letter-routing-key", ORDER_ROUTING_KEY)
> ```
> 消息带 `x-retry-count` 头，每重投一次 +1，到 3 次就不再走延迟队列。
> **这就是用 RabbitMQ 模拟 RocketMQ 的 `RECONSUME_LATER` 延迟梯度**——
> 我在实习项目里用的是 RocketMQ 原生延迟消息，这里是自己搭的等价方案。
>
> **2）业务失败 → 直接判失败（重试也没用）**
> 比如"商品已下架"、"券已过期"，重试一万次还是失败，
> 直接 `markOrderFail` 把订单置为 5（失败），填 `failReason`，并释放 clientToken，
> 用户可以重新下单。
>
> **3）兜底：定时补偿任务（线性退避）**
> ```java
> @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
> // 扫 status=4 且 update_time < NOW() - (retry_count+1)*2 分钟的订单
> ```
> 这是**独立于 MQ 的第二条保命链路**：万一 MQ 整个挂了、消息丢了，
> 订单表本身就成了"本地消息表"，补偿任务扫出来重新处理。
> 退避是线性的：第 n 次失败后要等 `(n+1) × 2` 分钟才再被扫到，
> 避免失败订单被疯狂重试拖垮系统。
> 达到 `MAX_PROCESS_RETRY = 10` 次后收敛为终态失败并释放幂等键，
> **保证"处理中"状态是有界的，不会永久卡住**。
>
> 补偿和 MQ 走的是**同一个 `processOrderCreate` 方法**，靠 CAS 幂等，
> 两边同时处理同一个订单也不会出问题（输家静默退出）。

### Q9.4 券核销了但订单失败了怎么办？（分布式事务问题）🔴

**标准答案：这是典型的跨服务一致性问题，我用"补偿 + 幂等"解决：**

> 场景：item-service 调 voucher-service 核销券成功了，
> 但本地推进订单状态时失败（比如 DB 抖动）→ 券被扣了但订单没成。
>
> **我的处理**：catch 到异常后，如果标记了 `voucherUsed[0] = true`，
> 立刻反向调用 `releaseVoucher` 退券：
> ```java
> catch (Exception e) {
>     if (voucherUsed[0]) {
>         VoucherReleaseDTO release = ...;
>         voucherClient.releaseVoucher(release);   // 补偿退券
>     }
> }
> ```
> 如果退券本身也失败了，只能打 error 日志告警人工处理。
>
> **券服务的两个接口都做了幂等**：
> - `useVoucher`：用**条件更新**做原子核销
>   ```java
>   update().eq(id).eq(userId).in(status, 1, 2).isNull(usedOrderNo)
>           .set(status, 3).set(usedOrderNo, orderNo);
>   ```
>   并发下只有一个能成功；如果同一个 `orderNo` 重复调用，直接返回原抵扣额，不重复扣。
> - `releaseVoucher`：只有 status=3 且 `orderNo` 匹配才退回，已退回的幂等放行。
>
> **为什么不用 Seata / 分布式事务框架？**
> 1. **性能**：Seata 的 AT 模式要加全局锁、写 undo_log，长事务会长时间占用行锁，
>    在高并发下单链路上是不可接受的
> 2. **复杂度**：引入 TC（事务协调器）增加运维成本和新的故障点
> 3. **业务可接受最终一致**：券核销和订单推进之间差几毫秒到几秒，
>    用户感知不到，用补偿就能兜住
>
> 所以我的方案本质是 **"本地消息表 + MQ + 对账补偿"的最终一致性**，
> 这是电商下单场景的主流做法。

### Q9.5 订单超时未支付怎么处理？

> `OrderExpireTask` 定时任务，每分钟扫一次：
> ```java
> .eq(status, STATUS_PENDING)                                  // 0 待支付
> .lt(createTime, LocalDateTime.now().minusMinutes(30))        // 超过 30 分钟
> .last("limit 50")
> ```
> 扫出来调用 `cancel()` 取消订单，如果用了券会**自动退券**，
> 避免"死单"一直占着券。每次只处理 50 条，避免一次锁太多行。
>
> 改进方向：数据量大了之后定时扫表有压力，可以改用
> **RabbitMQ 延迟消息**（下单时发一条 30 分钟的延迟消息，到点检查状态）
> 或者 **Redis 的过期监听**（注意 Redis 过期事件不保证及时，生产慎用）。

---

## 10. 部署与运维

### Q10.1 怎么部署的？Docker 起了什么？

**标准答案：**
> 中间件全部用 **Docker Compose 编排**，一个 `docker-compose.yml` 起：
> MySQL、Redis、Elasticsearch、Nacos、RabbitMQ、Nginx。
> 应用服务是 jar 包，用脚本 `java -jar` 启动，PID 记在 `deploy/pids/`。
>
> 部署流程脚本化，8 个脚本：
> ```
> 01-install-prereqs.sh    安装 JDK/Maven/Docker
> 02-start-infra.sh        启动中间件
> 03-build-services.sh     mvn clean package
> 04-start-services.sh     启动应用
> 05-stop-services.sh      停止
> 06-verify.sh             健康检查
> 07-post-reboot.sh        重启后恢复
> 08-install-autostart.sh  配置开机自启
> ```
> 前端是 Vue 2 + axios（CDN 引入，没有构建步骤），静态文件放
> `docker/nginx/html/`，由 Nginx 直接托管，
> `/api/*` 反向代理到 Gateway `127.0.0.1:10010`。
>
> 敏感配置走环境变量，`deploy/env.example` 是模板，
> 真实的 `env.sh` 加入了 `.gitignore`，不会被提交上去。

### Q10.2 4GB 内存的服务器怎么跑起来？

**标准答案（体现工程权衡）：**
> 主要是 ES 太吃内存（默认 JVM 堆就要 1-2GB）。我的做法：
> 1. **ES 可开关**：`START_ELASTICSEARCH=0 bash deploy/02-start-infra.sh` 可以跳过启动 ES
> 2. **应用侧自适应**：`ITEM_ELASTICSEARCH_ENABLED=false` 时 Bean 不装配，
>    搜索自动降级到 MySQL（这就呼应了前面 Q6.2 的设计——
>    **降级不只是为了高可用，也是为了低资源部署**）
> 3. 其余中间件限制容器内存（`docker-compose.yml` 里配 `mem_limit`）
>
> 这个设计的好处是**同一份代码能跑在 2GB 的测试机和 16GB 的生产机上**，
> 靠配置切换而不是改代码。

---

## 11. 项目难点 / 亮点 / 不足

### Q11.1 项目中遇到的最大难点是什么？

**推荐答案（选一个讲透，我建议讲"异步链路的可靠性"）：**

> 最大的难点是**秒杀链路"扣库存成功但订单丢失"的问题**。
>
> 一开始我的实现是：Lua 扣完库存 → 回到 Java 发 RabbitMQ → 消费者落库。
> 后来发现这中间有个致命窗口：**Lua 执行成功后进程如果宕机，消息根本没发出去**，
> 用户看到"抢购成功"，但数据库里永远没有这笔订单，而且库存凭空少了一个。
>
> 我的解决思路是**把"记录待办"塞进 Lua 脚本里**：
> 扣库存的同时 `XADD stream.orders`，让这两步变成原子的。
> 然后写一个 Dispatcher 线程用消费组去读 Stream，可靠地转发到 MQ。
> 消费组 + Pending List 重试保证了消息至少被转发一次。
>
> 这个方案其实就是 **Outbox 模式**，只是用 Redis Stream 替代了数据库发件箱表，
> 好处是不增加数据库写入压力。
>
> 后来我在商城下单模块把这个思路进一步强化成了
> "**本地事务受理 + MQ + 定时补偿**"——订单先落库当本地消息表，
> 补偿任务独立于 MQ 兜底，这样即使整个 MQ 挂了订单也不会丢。

**备选难点（如果面试官更关心一致性）：**
> 另一个难点是**幂等键的语义设计**。一开始我简单地在订单上加了
> `clientToken` 唯一索引，结果测试时发现：订单失败了之后，
> 用户拿着同一个 token 重新提交，会一直撞唯一索引，**永远下不了单**。
> 后来我把语义改成"**在途唯一**"——只在订单处于处理中/待支付时才占用幂等键，
> 进入终态就释放（置 null）。并且把 `DuplicateKeyException` 拆成三种语义处理：
> 幂等命中 / 终态占用 / 订单号冲突，分别走不同分支。
> 这个教训是：**幂等键不只是加个索引，要想清楚它的生命周期**。

### Q11.2 项目有哪些亮点？

> 1. **Redis Stream 做 Outbox**：解决了"扣库存和下发消息"的原子性问题，
>    这是很多教程项目会忽略的
> 2. **分级降级设计**：ES 可开关、可降级，Bean 层面 `required=false` 可选装配，
>    运行层面异常兜底，一套代码适配不同资源环境
> 3. **双拦截器分离**：把"Token 续期"和"强制登录"解耦，
>    零重复 IO 且配置灵活
> 4. **完整的失败处理体系**：瞬时故障走 30 秒延迟重试 ×3，
>    业务失败直接收敛终态，MQ 之外还有补偿任务线性退避兜底，
>    重试次数有上限保证中间态有界
> 5. **幂等贯穿全链路**：clientToken 幂等键、CAS 状态推进、
>    券的核销/退券幂等、消息消费的主键冲突幂等
> 6. **细节上的工程考虑**：线程池用有界队列防 OOM、
>    缓存 key 带 limit 防污染、空列表不写缓存、
>    rename 原子替换防半写、不用 ORDER BY RAND()

### Q11.3 ⚠️ 项目有什么不足？怎么改进？（必考题，要诚实）

**标准答案（主动暴露 + 给出方案，这才是高级工程师的样子）：**

> **1）一人一单没有数据库兜底**
> `voucher_order` 表只有 `idx_user_id`、`idx_voucher_id`，
> 没有 `UNIQUE KEY (user_id, voucher_id)`。Redis Set 一旦被清就会重复售卖。
> → 加唯一索引，消费端捕获 `DuplicateKeyException` 做幂等。
>
> **2）Feign 调用没有熔断降级**
> item-service 调 voucher-service 的核销接口，如果券服务整体不可用，
> 会拖慢甚至拖垮下单链路（虽然有重试，但会放大故障）。
> → 接入 Sentinel Feign 适配，配置 `fallback`，
> 券服务不可用时降级为"不使用优惠券下单"或返回友好提示。
>
> **3）ES 是全量同步**
> `EsSyncTask` 每次 `saveAll` 全表，数据量上万后会有性能问题。
> → 改成 Canal 订阅 binlog 增量同步，或者业务更新时发 MQ 事件。
>
> **4）补偿任务没有分布式锁**
> `OrderCompensateTask` 用 `@Scheduled`，多实例部署时会重复扫描。
> 虽然 `processOrderCreate` 幂等不会出错，但会浪费资源。
> → 加 ShedLock，或者迁移到 xxl-job 做分片广播
> （我在实习项目里用过 xxl-job，知道它的分片机制）。
>
> **5）Redis 是单点的**
> 所有缓存、分布式 ID、秒杀库存、Stream 都依赖同一个 Redis，
> 挂了整个系统不可用。→ Redis 哨兵/集群 + 本地 Caffeine 二级缓存。
>
> **6）没有压测数据**
> 目前的性能结论是设计推导，没有实测的 QPS 数据支撑。
> → 用 JMeter 压测秒杀接口，量化吞吐、P99 延迟、错误率，
> 这样讲性能才有说服力。（诚实说这点比编数据强一百倍）
>
> **7）搜索结果没有高亮**
> 简历里提到"关键词高亮"，但 ES 查询里我没写 HighlightBuilder，
> 这部分其实是没做完的。**如果面试官问到，要如实说明是规划中的功能。**

---

## 12. 通用技术追问

### Q12.1 MySQL 索引与事务

**Q：为什么用自增主键而不是 UUID？**
> InnoDB 的主键是**聚簇索引**，数据按主键顺序物理存储。
> 自增主键是顺序写入，直接追加到 B+ 树最右边，页满了开新页，效率高；
> UUID 是无序的，插入时要在 B+ 树中间找位置，可能造成**页分裂**和**碎片**，
> 而且主键长度大（36 字节 vs 8 字节），二级索引的叶子节点要存主键，
> 会导致所有二级索引都变大。

**Q：事务的隔离级别？你们用的哪个？**
> - 读未提交 / 读已提交 / 可重复读 / 串行化
> - MySQL InnoDB 默认 **可重复读（RR）**，靠 MVCC + Next-Key Lock 实现，
>   在 RR 下就解决了大部分幻读问题
> - 我项目里没有改过隔离级别，用的默认。
>   秒杀扣库存那条 `UPDATE ... WHERE stock > 0` 靠的是**行锁**（当前读），
>   不依赖 MVCC 快照。

**Q：MVCC 原理？**
> 靠三个东西：**隐藏字段**（DB_TRX_ID 事务ID、DB_ROLL_PTR 回滚指针）、
> **undo log 版本链**、**ReadView**。
> 读的时候生成一个 ReadView，记录当前活跃事务列表，
> 然后顺着 undo log 版本链找到"对我可见"的那个版本（trx_id 小于 ReadView 中最小活跃事务ID）。
> 这就是**快照读**（普通 SELECT）不加锁还能保证可重复读的原因。
> `SELECT ... FOR UPDATE`、`UPDATE`、`DELETE` 是**当前读**，会加锁。

**Q：什么情况下索引会失效？**
> 1. `LIKE '%xxx'` 前缀通配符
> 2. 索引列上用函数或表达式：`WHERE YEAR(create_time) = 2026`
> 3. 隐式类型转换：字段是 varchar，传了数字（我代码里显式 `toString()` 存 Hash 就是为了避开这个）
> 4. 违反最左前缀：联合索引 `(a,b,c)` 只查 `b`、`c`
> 5. `OR` 连接的列没有全部建索引
> 6. 优化器判断全表扫描更快（数据量小或区分度低）
> 7. `!=`、`NOT IN`、`IS NULL` 在某些情况下

### Q12.2 Redis

**Q：Redis 为什么快？**
> 1. **纯内存操作**，没有磁盘 IO
> 2. **单线程模型**：没有线程上下文切换和锁竞争
>   （6.0 之后是多 IO 线程，但命令执行还是单线程）
> 3. **IO 多路复用**：epoll，一个线程处理上万连接
> 4. **高效数据结构**：SDS、跳表、压缩列表、哈希表

**Q：Redis 持久化？**
> - **RDB**：某时刻的内存快照，二进制紧凑，恢复快；但**可能丢最后一次快照后的数据**
> - **AOF**：记录每条写命令，append 到文件；`appendfsync everysec` 最坏丢 1 秒
> - **混合持久化**（4.0+）：AOF 重写时把 RDB 内容写进 AOF 开头，兼顾速度和安全
>
> 对我项目的影响：分布式 ID 依赖 Redis 自增，如果 Redis 重启且 AOF 没刷盘，
> ID 可能重复——所以我在消费端做了主键冲突的幂等兜底（呼应 Q4.3）。

**Q：分布式锁怎么实现？有什么坑？**
> 基础版 `SET key value NX EX 10`，释放用 **Lua 脚本**（先 get 比对 value 再 del，保证原子）。
>
> 坑：
> 1. **释放了别人的锁**：A 的锁超时了，B 拿到锁，A 执行完把 B 的锁删了
>    → 用唯一 value（UUID）+ Lua 校验
> 2. **业务没执行完锁就过期了**：→ Redisson 的 **WatchDog 自动续期**（默认 30 秒，每 10 秒续一次）
> 3. **不可重入**：→ Redisson 用 Hash 结构记录重入次数
> 4. **主从切换丢锁**：主节点还没同步给从节点就挂了，从升主，锁丢了
>    → RedLock（争议较大，Redis 作者和 Martin Kleppmann 有过争论）
>
> 我的秒杀没用分布式锁，用的是 Lua 原子脚本（见 Q3.2）。

**Q：Redis 内存满了怎么办？**
> `maxmemory-policy`：
> - `noeviction`：不改，写操作报错（默认）
> - `allkeys-lru` / `volatile-lru`：LRU 淘汰
> - `allkeys-lfu` / `volatile-lfu`：LFU（4.0+，按访问频率，更准）
> - `allkeys-random`
> - `volatile-ttl`：淘汰剩余 TTL 最短的
>
> ⚠️ 这对我的秒杀是**真风险**：如果 Redis 触发 LRU 淘汰把 `seckill:order:{id}`
> 删了，一人一单就失效了。所以我建议把它设为 `noeviction` 或者
> 给秒杀的 key 设成不过期 + 靠 DB 唯一索引兜底。

### Q12.3 RabbitMQ

**Q：怎么保证消息不丢？**
> 三个环节都要防：
> 1. **生产者 → MQ**：
>    - `publisher-confirm`（确认消息到达交换机）
>    - `publisher-return`（消息无法路由时回调）
>    - 事务（性能差，不用）
> 2. **MQ 本身**：交换机、队列、消息全部 `durable` 持久化
> 3. **MQ → 消费者**：**手动 ACK**，处理成功才 `basicAck`，
>    失败 `basicNack(requeue=false)` 进死信
>
> 我项目里：队列和消息都持久化了，消费端手动 ACK。
> **生产者确认我没有显式配置**——但因为"订单已经落库 + 补偿任务兜底"，
> 即使消息丢了也不会丢单（这是用本地消息表换掉了生产者确认，反而更可靠）。

**Q：怎么保证消息不重复消费？**
> 完全不重复是做不到的，MQ 只保证 **at-least-once**（至少一次），
> 所以**必须靠消费端幂等**。我的做法：
> - 优惠券订单：`getById(orderId) != null` 直接 return；
>   捕获 `DuplicateKeyException` 直接 ACK
> - 商城订单：CAS 状态更新，只有 status=处理中 才会被推进
> - 券核销：条件更新 + `usedOrderNo` 判空
>
> 通用方案：**给每条消息一个全局唯一的 messageId**，
> 消费前查一下处理记录表（或 Redis SETNX），已处理就跳过。

**Q：消息堆积怎么办？**
> 1. **先定位**：是生产者太快，还是消费者太慢（消费端有 bug 导致重试？）
> 2. **临时扩容消费者**：增加消费者实例 + 增加 `prefetch` 并发数
> 3. **批量消费**：改成一次拉一批处理
> 4. **降级**：非核心消息先丢弃或转入专用队列慢慢消费
> 5. **根因优化**：消费端是不是有慢 SQL、同步 HTTP 调用

**Q：延迟消息怎么实现？**
> 我用了两种：
> - **死信 + TTL**：消息进一个无消费者的队列，设 `x-message-ttl`，
>   到期后死信路由到目标交换机（我商城订单的 30 秒重试就是这么做的）
> - **RabbitMQ 延迟插件** `rabbitmq_delayed_message_exchange`（更灵活，支持任意延迟）
> - 另外 RocketMQ 原生支持 18 个延迟级别（我在实习项目里用的就是这个）
>
> TTL 方案的坑：**消息过期检查是惰性的**，只有当消息到达队列头部才会被判定过期。
> 如果队列里第一条消息 TTL 是 1 小时、第二条是 10 秒，
> 第二条要等第一条过期后才能被投递。

### Q12.4 Java / JVM 基础

**Q：HashMap 底层？**
> 数组 + 链表 + 红黑树。
> - 默认容量 16，负载因子 0.75，扩容翻倍
> - `hash(key)` 高位参与运算：`h ^ (h >>> 16)` 减少碰撞
> - 链表长度 > 8 且数组长度 ≥ 64 时转红黑树；< 6 退化回链表
> - **线程不安全**：JDK7 扩容时头插法会形成环导致死循环；
>   JDK8 改尾插法解决了死循环，但并发 put 仍会丢数据
> - 线程安全用 `ConcurrentHashMap`（JDK8 是 CAS + synchronized 锁单个桶，
>   比 JDK7 的 Segment 分段锁粒度更细）

**Q：synchronized 和 ReentrantLock 区别？**
> | | synchronized | ReentrantLock |
> |---|---|---|
> | 层面 | JVM 关键字，自动释放 | JDK API，必须 `finally` 手动 unlock |
> | 可中断 | 不可 | `lockInterruptibly()` |
> | 公平锁 | 非公平 | 可配公平/非公平 |
> | 条件队列 | `wait/notify` | 多个 `Condition`，可精准唤醒 |
> | 尝试加锁 | 无 | `tryLock(timeout)` |
> | 性能 | JDK6 后有锁升级优化，差距很小 |
>
> 锁升级过程：**无锁 → 偏向锁 → 轻量级锁（CAS 自旋）→ 重量级锁（操作系统互斥量）**，
> 只能升级不能降级。

**Q：线程池参数？为什么不用 Executors？**
> 七个参数：核心线程数、最大线程数、空闲存活时间、时间单位、
> 工作队列、线程工厂、拒绝策略。
>
> **不用 `Executors` 的原因**（阿里规约明确禁止）：
> - `newFixedThreadPool` / `newSingleThreadExecutor`：队列是
>   `LinkedBlockingQueue` 无界（默认 `Integer.MAX_VALUE`），
>   **任务堆积会 OOM**
> - `newCachedThreadPool`：最大线程数 `Integer.MAX_VALUE`，
>   **线程无限创建会 OOM**
>
> 所以必须手动 `new ThreadPoolExecutor(...)` 指定有界队列和拒绝策略。
> 我的 `CacheClient` 里就是这么写的（有界队列 100 + AbortPolicy）。
>
> 四种拒绝策略：`AbortPolicy`（抛异常，默认）、`CallerRunsPolicy`（调用者线程执行，
> 天然的负反馈）、`DiscardPolicy`（静默丢弃）、`DiscardOldestPolicy`（丢最老的）。

**Q：ThreadLocal 原理和内存泄漏？**
> 每个 Thread 有一个 `ThreadLocalMap`，key 是 ThreadLocal 的**弱引用**，
> value 是强引用。
> ThreadLocal 被回收后，key 变成 null，但 value 还在，
> 只要线程不死（线程池复用）就一直占着内存 → **内存泄漏**。
> 所以用完必须 `remove()`。我的拦截器 `afterCompletion` 里就做了这件事。

---

## 13. 反问环节

> 最后面试官问"你有什么想问的"，选 2-3 个问，显得有思考。

**推荐问法：**
1. 「这个岗位所在的团队主要负责哪块业务？后端服务的规模和 QPS 大概是什么量级？」
2. 「团队目前的技术栈和基建情况？比如有没有统一的微服务框架、部署平台、可观测体系？」
3. 「新人入职后大概会接触什么样的需求？有没有导师机制？」
4. 「您觉得做好这个岗位，最需要的能力是什么？」

**不要问：** 薪资（HR 环节再问）、加班情况、太泛泛的问题。

---

## 附：面试前速查清单

**必背数字**
| 项 | 值 |
|---|---|
| 网关端口 | 10010 |
| user / item / voucher | 8081 / 8082 / 8083 |
| Token TTL | 36000 秒（10 小时） |
| 验证码 TTL | 2 分钟 |
| 首页缓存逻辑过期 | 30 分钟 |
| 推荐缓存 TTL | 24 小时 |
| ID 起始时间戳 | 1640995200（2022-01-01） |
| ID 序列号位数 | 32 位，可用到 2090 年 |
| 热点限流 | 100 QPS / 秒 / 每 token |
| 延迟重试 | 30 秒 × 3 次 |
| 补偿退避 | (n+1) × 2 分钟，上限 10 次 |
| 订单超时 | 30 分钟 |
| ES 返回条数 | 20 |
| 推荐条数 | 8 |

**必背链路**：秒杀 8 步（Q3.1）、登录 5 步（Q2.1）、下单两段（Q9.1）

**必背设计决策（要能说清为什么）**
- Lua 而不是分布式锁 → 原子 + 无锁开销
- Stream Outbox 而不是直接发 MQ → 解决原子性窗口
- 逻辑过期而不是互斥锁 → 可用性优先、不阻塞
- 游标分页而不是 OFFSET → 避免深分页
- 双拦截器 → 正交职责分离
- 手动 ACK + 死信 + 补偿 → 三级可靠性
- 不用 Seata → 性能 + 最终一致可接受

**主动暴露的改进点（诚实 > 完美）**
- 一人一单无 DB 唯一索引
- Feign 无熔断
- ES 全量同步
- 补偿任务无分布式锁
- 无压测数据
- 搜索高亮未实现
