# IntellRecipe 智能饮食管理平台

基于 Spring Cloud 微服务架构的智能饮食管理与电商系统，提供食材浏览、商品购买、优惠券秒杀、饮食记录与热量管理等功能。

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 2.7.18 |
| 微服务 | Spring Cloud | 2021.0.8 |
| 注册中心 | Spring Cloud Alibaba / Nacos | 2021.0.5.0 / v2.2.0 |
| ORM | MyBatis-Plus | 3.5.3.1 |
| 数据库 | MySQL | 5.7 |
| 缓存 | Redis | 6.2 |
| 搜索引擎 | Elasticsearch | 7.17.24 |
| 消息队列 | RabbitMQ | 3.13 |
| 网关 | Spring Cloud Gateway | — |
| 反向代理 | Nginx | 1.18.0 |
| JDK | Java | 1.8 |
| 前端 | Vue 2 + axios（CDN 引入） | — |

## 项目结构

```
IntellRecipe/
├── pom.xml                          # 父 POM，统一依赖管理
├── intellrecipe-common/             # 公共模块（实体、DTO、工具类、拦截器）
├── intellrecipe-gateway/            # 网关服务（路由分发、跨域处理）
├── user-service/                    # 用户服务（登录、注册、个人信息）
├── item-service/                    # 商品服务（商品、食材、商家、购物车、ES 搜索）
├── voucher-service/                 # 优惠券服务（秒杀、优惠券管理、订单）
├── diet-service/                    # 饮食服务（饮食记录、热量管理）
├── docker/                          # Docker 基础设施配置
│   ├── docker-compose.yml           # MySQL/Redis/ES/Nacos/RabbitMQ/Nginx
│   ├── mysql/init/                  # 数据库初始化脚本
│   ├── nginx/                       # Nginx 配置与前端静态页面
│   └── redis/conf/                  # Redis 配置
└── deploy/                          # 部署脚本
    ├── 01-install-prereqs.sh        # 安装前置依赖
    ├── 02-start-infra.sh            # 启动基础设施
    ├── 03-build-services.sh         # 构建服务
    ├── 04-start-services.sh         # 启动应用服务
    ├── 05-stop-services.sh          # 停止应用服务
    ├── 06-verify.sh                 # 验证部署
    ├── 07-post-reboot.sh            # 重启后恢复
    ├── 08-install-autostart.sh      # 安装开机自启
    ├── env.example                  # 环境变量模板
    └── docker.env.example           # Docker 环境变量模板
```

## 服务说明

| 服务 | 端口 | 路由前缀 | 功能 |
|------|------|----------|------|
| intellrecipe-gateway | 10010 | — | API 网关，路由分发、负载均衡 |
| user-service | 8081 | `/user/**`、`/upload/**` | 手机号验证码登录、用户信息管理、文件上传 |
| item-service | 8082 | `/items/**`、`/ingredient/**`、`/product/**`、`/shop/**`、`/merchant/**`、`/cart/**`、`/admin/**`、`/search/**` | 商品/食材/商家管理、购物车、ES 搜索、后台管理 |
| voucher-service | 8083 | `/voucher/**`、`/vouchers/**`、`/seckill/**`、`/voucher-orders/**` | 优惠券管理、秒杀下单、订单管理 |
| diet-service | — | `/diet/**` | 饮食记录、热量计算与统计 |

## 核心功能

### 1. 用户体系
- 手机号 + 验证码登录注册（阿里云短信服务发送验证码）
- JWT Token 鉴权，Redis 存储 Token 会话
- 全局登录拦截器自动刷新 Token 有效期
- 个人中心：昵称/头像资料编辑、图片上传
- 我的优惠券查看

### 2. 食材与智能对话
- 食材列表游标分页查询（避免深分页性能问题）
- Elasticsearch 全文搜索食材（关键词高亮，ES 不可用时降级 MySQL）
- 食材详情查看（含热量、营养信息）
- 今日推荐食材（Redis 缓存 + 定时任务预热）
- AI 智能对话（基于食谱知识库的问答推荐）
- 饮食记录管理：添加/删除食材条目、今日食谱汇总与热量统计

### 3. 电商与营销
- 商家列表游标分页查询
- 商品列表与详情查询（按商家筛选）
- 购物车：添加/删除/改量、单选/全选、批量删除、清空
- 优惠券列表查询（按店铺）
- 优惠券购买下单
- 后台管理：食材/商家/商品的增删改查（含缓存自动清理）

## 数据库设计

| 表名 | 说明 |
|------|------|
| `user` | 用户信息（手机号、昵称、身高体重等） |
| `merchant` | 商家信息 |
| `product` | 商品信息 |
| `product_ingredient` | 商品-食材关联表 |
| `ingredient` | 食材总表（含热量数据） |
| `cart` | 购物车 |
| `voucher` | 优惠券（普通券/秒杀券） |
| `seckill_voucher` | 秒杀券扩展（库存、时间） |
| `voucher_order` | 优惠券订单（雪花算法 ID） |
| `diet_log` | 饮食记录 |

## 快速开始

### 环境要求

- JDK 1.8
- Maven 3.6+
- Docker & Docker Compose
- 4GB+ 内存（Elasticsearch 可选）

### 1. 启动基础设施

```bash
# 配置环境变量
cp deploy/docker.env.example docker/.env
# 编辑 docker/.env，填写 MySQL/Redis/RabbitMQ 密码

# 启动中间件
cd docker
docker compose up -d
```

> 4GB 内存服务器可跳过 Elasticsearch：`START_ELASTICSEARCH=0 bash deploy/02-start-infra.sh`

### 2. 配置应用环境变量

```bash
cp deploy/env.example deploy/env.sh
# 编辑 deploy/env.sh，填写数据库、Redis、Nacos、RabbitMQ、短信服务等配置
```

### 3. 构建与启动

```bash
# 构建所有服务
mvn clean package -DskipTests

# 或使用部署脚本
bash deploy/03-build-services.sh
bash deploy/04-start-services.sh
```

### 4. 验证部署

```bash
bash deploy/06-verify.sh
```

访问 `http://localhost` 即可打开前端页面。

## 环境变量

| 变量名 | 说明 |
|--------|------|
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` | MySQL 连接配置 |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | MySQL 认证 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接配置 |
| `NACOS_SERVER_ADDR` | Nacos 地址 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | RabbitMQ 连接配置 |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | RabbitMQ 认证 |
| `ELASTICSEARCH_URIS` | Elasticsearch 地址 |
| `ITEM_ELASTICSEARCH_ENABLED` | 是否启用 ES（`false` 时降级为 MySQL 搜索） |
| `ALIYUN_SMS_ACCESS_KEY_ID` / `ALIYUN_SMS_ACCESS_KEY_SECRET` | 阿里云短信密钥 |
| `ALIYUN_SMS_SIGN_NAME` / `ALIYUN_SMS_TEMPLATE_CODE` | 短信签名与模板 |

## 架构概览

```
浏览器
  │
  ▼ :80
Nginx ── 静态页面 + 反向代理 /api/* → 127.0.0.1:10010
  │
  ▼
Gateway (10010) ── 路由分发 + 负载均衡
  │
  ├──► user-service (8081)
  ├──► item-service (8082) ──► Elasticsearch
  ├──► voucher-service (8083) ──► RabbitMQ
  └──► diet-service
         │
         ▼
  MySQL (3307) / Redis (6379) / Nacos (8848)
```

## 部署

详细的云服务器部署流程见 [deploy/README.md](deploy/README.md)，架构设计详见 [ARCHITECTURE.md](ARCHITECTURE.md)。

```bash
# 一键部署流程（服务器端）
bash deploy/01-install-prereqs.sh
bash deploy/02-start-infra.sh
bash deploy/03-build-services.sh
bash deploy/04-start-services.sh
bash deploy/06-verify.sh

# 重启后恢复
bash deploy/07-post-reboot.sh

# 安装开机自启
sudo bash deploy/08-install-autostart.sh
```

## License

本项目仅供学习交流使用。