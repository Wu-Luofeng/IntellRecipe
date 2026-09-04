# IntellRecipe 本地开发（localhost 运行 + 云端中间件）

> 目标：把「改一行代码 → push → 服务器 pull → 重新构建重启」的慢循环，改成**本机直接跑全部服务**，
> 中间件（MySQL / Redis / RabbitMQ / Elasticsearch）**仍使用云服务器** `129.204.203.184`，不动线上数据与容器。

---

## 一、整体结构

```
本机浏览器  http://127.0.0.1:8080   （serve-frontend.ps1 可选）
   │  /api/*  (去前缀转发)
   ▼
Gateway 127.0.0.1:10010
   │  lb://（服务注册到【本机 Nacos】，与云端注册中心完全隔离）
   ├──► user-service    :8081
   ├──► item-service    :8082
   ├──► voucher-service :8083
   ├──► diet-service    :8084
   └──► recipe-service  :8085
   │
   │  datasource / redis / rabbitmq / es 的地址均为 127.0.0.1
   ▼
start-tunnel.ps1（SSH 隧道）────► 云服务器 129.204.203.184 上的容器
```

关键点：

- **服务在本机启动**，配置无需指向公网 IP：`application.yml` 默认就是 `127.0.0.1`，
  MySQL 3307 / Redis 6380 / RabbitMQ 5672 / ES 9200 由 **SSH 隧道**映射到本机回环地址。
- **注册中心用本机独立 Nacos**（`start-nacos-local.ps1`）。不要直连云服务器 Nacos：
  云端 Nacos 的 gRPC 端口(9848)未对外开放，且混合注册会造成「影子实例」，线上网关可能把流量
  负载到本机导致线上故障。
- 云端服务器完全不动：`deploy/env.sh` 依然注入线上环境变量。

---

## 二、一次性准备

### 0. 推送前先备份云服务器上的 yml（重要！）

取消对 `application.yml` 追踪的提交一旦 `push`，云端 `git pull` 会把这些文件删除。
执行顺序建议：**先在云服务器备份，再 push**：

```powershell
ssh ubuntu@129.204.203.184 'mkdir -p ~/yml-backup; for m in diet-service intellrecipe-gateway item-service recipe-service user-service voucher-service; do cp ~/IntellRecipe/$m/src/main/resources/application.yml ~/yml-backup/$m.yml; done; echo backed up'
```

> 即便漏了这步也没关系：`deploy/03-build-services.sh` 现在构建前会自动
> `cp application.example.yml application.yml` 补回缺失配置，再执行 `mvn package`，
> 之后部署脚本照常运行（线上值仍来自 `env.sh`）。

### 1. 建立 SSH 隧道（保持窗口开启）

```powershell
powershell -ExecutionPolicy Bypass -File deploy/local-dev/start-tunnel.ps1
```

默认转发（只绑定 127.0.0.1，不暴露局域网）：

| 本地端口 | 云端 | 用途 |
|---|---|---|
| 3307 | 3307 | MySQL |
| 6380 | 6379 | Redis（本机 6379 被本地 Redis 服务占用，故本地用 6380） |
| 5672 | 5672 | RabbitMQ |
| 9200 | 9200 | Elasticsearch REST |

验证：另开终端 `Test-NetConnection 127.0.0.1 -Port 3307`，返回 `True` 即可。

> ℹ️ **Redis 使用 6380 端口的原因**：本机 Windows 服务 `Redis` 已占用 6379（`Get-Service Redis`
> 可见），因此云端 6379 被映射到**本机 6380**。五个业务服务的本机 `application.yml`
> 已把 `redis.port` 默认值改为 `6380`，无需任何额外操作；云端服务器仍由 `env.sh` 注入
> `REDIS_PORT=6379`，互不影响。若你日后停用了本机 Redis 想改回 6379：把隧道脚本的
> `@{ Local = 6380; Remote = 6379 }` 改成 `@{ Local = 6379; Remote = 6379 }`，并将各服务
> `application.yml` 的 `redis.port` 改回 `6379`。


### 2. 启动本机隔离 Nacos

```powershell
powershell -ExecutionPolicy Bypass -File deploy/local-dev/start-nacos-local.ps1
```

- 首次运行自动下载 Nacos 2.2.0（约 100 MB）到 `%USERPROFILE%\.local-dev\`。
- 控制台：`http://127.0.0.1:8848/nacos`（`nacos` / `nacos`）。
- 停止：`%USERPROFILE%\.local-dev\nacos\bin\shutdown.cmd`。

### 3. 构建

```powershell
mvn clean package -DskipTests
```

（先确保 `intellrecipe-common` 已 install/打包进各模块构建链，按根 pom 正常构建即可。）

### 4. 启动各服务

推荐用 IDEA 依次运行以下主类（也可 `java -jar <module>/target/<module>-*.jar`）：

| 服务 | 端口 | 主类 |
|---|---|---|
| user-service | 8081 | `com.springboot.intellrecipe.UserApplication` |
| item-service | 8082 | `com.springboot.intellrecipe.item.ItemApplication` |
| voucher-service | 8083 | `com.springboot.intellrecipe.voucher.VoucherApplication` |
| diet-service | 8084 | `com.springboot.intellrecipe.diet.DietApplication` |
| recipe-service | 8085 | `com.springboot.intellrecipe.recipe.RecipeApplication` |
| intellrecipe-gateway | 10010 | `com.springboot.intellrecipe.gateway.GatewayApplication` |

启动顺序不限，建议先业务服务后 gateway。全部注册到本机 Nacos 后，Nacos 控制台
「服务管理 → 服务列表」应能看到 6 个服务。

可选环境变量（不设置即用默认）：

- 想关闭 ES（连不上 / 不想连时搜索自动降级 MySQL）：
  `ITEM_ELASTICSEARCH_ENABLED=false`（本机默认 `true`，走隧道连云端 ES）。
- 想测试 DeepSeek AI 对话：启动 recipe-service 前设置 `DEEPSEEK_API_KEY=<你的key>`。
- 短信验证码：不设置 `ALIYUN_SMS_*` 时 user-service 走开发模式，验证码直接在响应中返回。

### 5. 验证后端

```powershell
curl http://127.0.0.1:10010/items/ingredient/list?limit=5
# 或直连服务： curl http://127.0.0.1:8082/ingredient/list?limit=5
```

### 6. （可选）本地打开前端页面

不需要 Docker/Nginx：

```powershell
powershell -ExecutionPolicy Bypass -File deploy/local-dev/serve-frontend.ps1
# 浏览器打开 http://127.0.0.1:8080
```

- 静态文件来自 `docker/nginx/html`；`/api/*` 反代到本机 gateway(10010)；`/uploads/*`
  指向 `%USERPROFILE%\IntellRecipe\uploads`。
- 若提示 Access Denied，先管理员执行一次：
  `netsh http add urlacl url=http://127.0.0.1:8080/ user=Everyone`

---

## 三、配置管理约定

| 文件 | 是否提交 | 说明 |
|---|---|---|
| `*/src/main/resources/application.example.yml` | ✅ git 跟踪 | 各服务模板（环境变量占位符），新机器/服务器参考或恢复用 |
| `*/src/main/resources/application.yml` | ❌ 已加入 .gitignore | 本机私有运行配置，内容可随意改；改坏用 `example` 复制覆盖恢复 |
| `deploy/env.sh` | ❌ 已忽略 | 云服务器真实环境变量 |
| `docker/docker-compose.yml` | ✅ git 跟踪 | 云端基础设施编排，**不要删除**，也不要提交本地临时改动 |
| `deploy/local-dev/*.ps1` | ✅ git 跟踪 | 本目录三个辅助脚本 |

---

## 四、常见问题

1. **本地端口被占用**：例如本地已装有 MySQL(3306)/Redis(6379) 或端口被别的进程占用，
   `start-tunnel.ps1` 会打印 `bind ... Address already in use`，但**其它隧道不受影响**
   （脚本未开 `ExitOnForwardFailure`）。当前云端 Redis 已避开冲突端口 6379、统一映射到
   本机 6380（见上文说明）。如需调整其它映射：改脚本顶部 `$Forwards` 列表，并同步改各服务
   本机 `application.yml` 对应端口。
2. **服务间 lb 路由不通 / 网关 503**：确认本机 Nacos 已启动、各服务启动成功且已注册
   （Nacos 控制台服务列表可见）。不要改 `server.address=127.0.0.1`：Nacos 默认注册本机
   网卡 IP（如 `192.168.x.x`），服务必须监听所有网卡（默认 `0.0.0.0`）才能被本机 lb 访问。
3. **第一次运行 Windows 防火墙弹窗**：允许 Java 监听专用/公用网络。
4. **上传目录**：默认 `%USERPROFILE%\IntellRecipe\uploads`，由 user-service 的
   `upload.dir` 决定，可在本机 `application.yml` 改。
