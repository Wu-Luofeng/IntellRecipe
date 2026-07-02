# IntellRecipe 项目架构文档

> 本文档记录项目的整体架构、服务端口、部署方式及关键流程，用于跨对话窗口保持上下文。

---

## 一、云服务器信息

> 📌 **开发环境统一部署目标**：本项目的开发/测试环境统一部署到下方云服务器，所有服务变更最终都要在此服务器上运行验证。

| 项目 | 值 |
|------|-----|
| 云厂商 | 腾讯云 |
| 用户名 | `ubuntu` |
| 主机名 | `VM-0-15-ubuntu` |
| 公网 IP | `129.204.203.184` |
| 系统 | Ubuntu |
| SSH 端口 | `22` |
| SSH 密码 | 见 `deploy/SERVER_ACCESS.local.md`（本地私有，已 gitignore） |
| **项目根目录** | `/home/ubuntu/IntellRecipe`（即 `~/IntellRecipe`） |
| compose 工作目录 | `~/IntellRecipe/docker` |
| 启动基础设施 | `cd ~/IntellRecipe/docker && docker compose up -d` |

### SSH 连接

```bash
# 标准登录（会提示输入密码，密码见 deploy/SERVER_ACCESS.local.md）
ssh ubuntu@129.204.203.184

# 免密登录配置见 deploy/SERVER_ACCESS.local.md
```

> 🔐 真实密码、密钥等敏感凭证只记录在 `deploy/SERVER_ACCESS.local.md`（已被 `.gitignore` 排除），
> 仓库内文档不保存明文密码。如需获取密码，请查看该本地文件。

### 服务器目录结构（实际情况）

```
/home/ubuntu/
└── IntellRecipe/               ← 项目根，git clone 在此，非 /opt/intellrecipe
    ├── deploy/
    │   ├── env.sh              ← 本地 gitignore，含真实凭证，需手动 scp 同步
    │   ├── env.example         ← 模板，可提交
    │   ├── 04-start-services.sh
    │   └── pids/               ← 运行时生成，存各服务 PID
    ├── docker/
    │   ├── docker-compose.yml
    │   ├── .env                ← docker 基础设施密码，需手动创建
    │   └── nginx/html/         ← 静态前端页面
    ├── logs/                   ← 运行时生成，各服务日志
    ├── user-service/target/
    ├── item-service/target/
    ├── voucher-service/target/
    └── intellrecipe-gateway/target/
```

> **AI 注意**：服务器上项目路径是 `~/IntellRecipe`，**不是** `/opt/intellrecipe`。
> 所有 scp / ssh 命令目标路径均以 `/home/ubuntu/IntellRecipe/` 为根。

### 部署工作流

**标准流程：本机 push → 服务器 pull → 重新打包 → 重启服务**

```powershell
# 1. 本机提交并推送（PowerShell）
git add .
git commit -m "xxx"
git push origin main
```

```bash
# 2. 服务器拉取并重新打包（SSH 进去执行）
cd ~/IntellRecipe
git pull origin main
mvn clean package -DskipTests

# 3. 重启所有服务
bash deploy/05-stop-services.sh
bash deploy/04-start-services.sh
```

### SCP 仅用于不能进 git 的文件

`deploy/env.sh` 含真实凭证，已在 `.gitignore` 中排除，只能用 scp 手动同步：

```powershell
# 本机修改 env.sh 后，手动同步到服务器（唯一需要 scp 的场景）
scp deploy/env.sh ubuntu@129.204.203.184:~/IntellRecipe/deploy/env.sh
```

---

## 二、整体架构

```
外部请求
    │
    ▼  :80 (host network)
┌─────────┐
│  Nginx  │  静态页面 → /usr/share/nginx/html
│ 1.18.0  │  /api/* → proxy_pass http://127.0.0.1:10010/
└────┬────┘
     │ 127.0.0.1:10010 (同宿主机，host network)
     ▼
┌──────────────────┐
│  Gateway         │  Spring Cloud Gateway
│  port: 10010     │  路由规则见下方，注册到 Nacos
└──────┬───────────┘
       │  lb://（Nacos 负载均衡）
  ┌────┼────────────┐
  ▼    ▼            ▼
user  item      voucher
8081  8082       8083
  │    │  │        │  │
  │    │  ES       │  RabbitMQ
  │    │  9200      │  5672
  └────┴──┴─────────┴──┤
                        │
              MySQL(3307)  Redis(6379)  Nacos(8848)
```

---

## 三、Spring Boot 服务

| 服务 | 端口 | Nacos 注册名 | 路由路径 |
|------|------|-------------|---------|
| intellrecipe-gateway | 10010 | intellrecipe-gateway | — |
| user-service | 8081 | user-service | `/user/**` |
| item-service | 8082 | item-service | `/items/**` `/search/**` `/shop/**` `/merchant/**` `/product/**` `/ingredient/**` `/shop-type/**` |
| voucher-service | 8083 | voucher-service | `/voucher/**` `/seckill/**` `/upload/**` `/vouchers/**` `/voucher-orders/**` |

服务均以 `nohup java -jar xxx.jar &` 或 `java -jar` 形式直接运行在宿主机，**不在 Docker 里**。  
JAR 构建位置：各模块 `target/` 目录，例如 `item-service/target/item-service-*.jar`。

---

## 四、Docker 基础设施

| 容器名 | 镜像 | 宿主机端口 | 说明 |
|--------|------|-----------|------|
| intellrecipe-nginx | nginx:1.18.0 | **host 网络，无端口映射** | `network_mode: host`，直连 127.0.0.1:10010 |
| intellrecipe-mysql | mysql:5.7 | 3307→3306 | 数据库，字符集 utf8mb4 |
| intellrecipe-redis | redis:6.2 | 6379 | 需 `REDIS_PASSWORD` 环境变量 |
| intellrecipe-es | elasticsearch:7.17.24 | 9200, 9300 | single-node 模式 |
| intellrecipe-nacos | nacos/nacos-server:v2.2.0 | 8848, 9848 | standalone 模式 |
| intellrecipe-rabbitmq | rabbitmq:3.13-management | 5672, 15672 | management 面板在 :15672 |

### 关键注意事项：Nginx 必须用 `network_mode: host`

- **禁止**在 nginx 的 `proxy_pass` 或 `upstream` 里写 `host.docker.internal`  
  原因：Nginx 在配置加载期进行 DNS 解析，bridge 网络下 `host.docker.internal` 不可达，导致 `[emerg] host not found in upstream` → 容器反复 Restarting。
- **正确配置**：`network_mode: host` + `proxy_pass http://127.0.0.1:10010/`，Nginx 与宿主机共享网络栈，直连 Gateway。
- `docker ps` 验证：`network_mode: host` 时 Nginx 行**不会显示** `0.0.0.0:80->80` 端口映射。

---

## 五、Nginx 配置要点

文件位置：`docker/nginx/conf/nginx.conf`（挂载到 `/etc/nginx/nginx.conf`）

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:10010/;   # 注意末尾 /，路径不带 /api 前缀转发给 Gateway
    proxy_connect_timeout 15s;
    proxy_send_timeout 120s;
    proxy_read_timeout 120s;
}
```

静态页面挂载：`docker/nginx/html/` → `/usr/share/nginx/html/`

---

## 六、前端静态页面

| 页面 | 文件 | 说明 |
|------|------|------|
| 登录 | `login.html` | 手机号+验证码登录，写 localStorage token |
| 首页 | `index.html` | 商店列表入口 |
| 商店列表 | `shop-list.html` | 分类浏览 |
| 商品列表 | `product-list.html` | 商品展示 |
| 商品详情 | `product-detail.html` | 商品信息 |
| 食材列表 | `ingredient-list.html` | `/api/ingredient/list` |
| 商家详情（含领券） | `product-list.html?merchantId=xx&tab=vouchers` | `/api/vouchers/list/{shopId}` |
| 优惠券详情 | `voucher-detail.html` | 秒杀/领取入口 |

页面统一通过 `/api/` 前缀调用后端，由 Nginx 转发到 Gateway。CDN 使用 BootCDN（Vue / axios）。

---

## 七、秒杀/优惠券下单核心流程

```
前端 POST /api/seckill/{voucherId}
    │
    ▼
voucher-service（8083）
    │ 1. Lua 脚本原子判断：库存 & 用户是否已购
    │    成功 → 写订单事件到 Redis Stream（outbox）
    │    失败 → 返回错误码
    │
    ▼
RedisStreamDispatcher（后台线程）
    │ 2. 从 Stream 消费组拉取事件
    │ 3. 发送到 RabbitMQ（开启 Publisher Confirm + Return）
    │ 4. ack 成功 → XACK + XDEL Stream 中该事件
    │    nack/route fail → 不 XACK，留 pending 重试
    ▼
RabbitMQ → VoucherMqListener
    │ 5. 手动 ACK，落库（DB 幂等：orderId 唯一 / userId+voucherId 唯一）
    │ 6. 失败 → 进死信队列（DLQ），记录 dead_letter 表
```

关键配置：
- RabbitMQ `publisher-confirm-type: correlated` + `publisher-returns: true`
- 消费端 `acknowledge-mode: manual` + `prefetch: 1`
- Lua 脚本：`voucher-service/src/main/resources/lua/seckill.lua`

---

## 八、环境变量（`.env` 文件，位于 `docker/`）

```
MYSQL_ROOT_PASSWORD=
MYSQL_DATABASE=intell_recipe
REDIS_PASSWORD=
RABBITMQ_DEFAULT_USER=
RABBITMQ_DEFAULT_PASS=
ES_JAVA_OPTS=-Xms256m -Xmx256m
```

Spring Boot 服务同理，通过启动参数或系统环境变量注入：  
`MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_PASSWORD`, `REDIS_HOST`, `REDIS_PASSWORD`,  
`NACOS_SERVER_ADDR`, `RABBITMQ_HOST`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`

---

## 九、常用运维命令

```bash
# 在服务器 docker 目录下
cd ~/IntellRecipe/docker

# 启动全部基础设施
docker compose up -d

# 单独重建 nginx（修改 nginx.conf 后必须执行）
docker compose up -d --force-recreate nginx

# 查看 nginx 日志
docker logs intellrecipe-nginx --tail 30

# 验证 nginx 用了 host 网络（正确时不显示 ->80 映射）
docker ps

# 拉取最新代码
cd ~/IntellRecipe && git pull origin main

# 如果服务器文件与仓库不一致，强制覆盖修复脚本
bash ~/IntellRecipe/docker/fix-nginx-on-server.sh

# 查看各服务进程
ps aux | grep jar

# 重启某个服务（以 item-service 为例）
cd ~/IntellRecipe/item-service
nohup java -jar target/item-service-*.jar > logs/item.log 2>&1 &
```

---

## 十、常见问题排查

### 登录成功但 `user` 表无数据

1. 确认查的是 **3307** 端口、`intell_recipe` 库（与 `deploy/env.sh` 一致）。
2. 登录后看 `logs/user-service.log` 是否有 `新用户注册成功: userId=...`。
3. 若之前登录过，Redis 里 token 可能 **没有 userId**（旧 bug），需退出后 **重新登录** 获取新 token。

### 抢购接口返回订单号但 `voucher_order` 无记录

| 券类型 | 落库方式 | 排查 |
|--------|----------|------|
| 普通券 (type=0) | 同步写库 | 查 `user-service` / `voucher-service` 日志是否有「订单用户ID为空」 |
| 秒杀券 (type=1) | Redis Stream → RabbitMQ → 消费者写库 | 查 `voucher-service.log` 中 `接收到订单消息`、`deductStock`；查 `tb_dead_letter`；确认 RabbitMQ 容器正常 |

秒杀券 Redis 库存 key：`seckill:stock:{voucherId}`，首次下单时会从 MySQL `seckill_voucher.stock` 同步。

---

## 十二、AI 助手工作规范

> **每次对话开始时必须读取本文档**，保持上下文。

### 代码修改后必须执行 git add + commit

每次完成代码修改后，**不等用户提醒**，主动执行：

```powershell
git -C "E:\study\大三\springboot\IntellRecipe" add -A
git -C "E:\study\大三\springboot\IntellRecipe" commit -m "feat/fix/refactor: 简短描述"
```

注意事项：
- Windows PowerShell 不支持 `&&` 连接命令，要**分两行**执行
- commit message 用英文，格式 `feat:` / `fix:` / `refactor:` 前缀
- Heredoc (`<<'EOF'`) 在 PowerShell 中不可用，`-m` 只传单行字符串

- [ ] 图片迁移至 OSS（阿里云/腾讯云 COS/MinIO）
- [ ] CDN 加速图片资源
- [ ] 前端图片懒加载 + 骨架屏
- [ ] 商品列表缩略图 vs 详情高清图分离
- [ ] 图片统一转 WebP 格式
