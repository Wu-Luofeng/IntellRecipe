#!/usr/bin/env bash
# 在 Linux 云服务器上执行：修复 Nginx 因 host.docker.internal 解析失败而反复 Restarting。
# 用法（在仓库 docker 目录旁或本脚本所在目录执行均可）：
#   bash docker/fix-nginx-on-server.sh
# 或先 cd 到 IntellRecipe/docker 再：
#   bash fix-nginx-on-server.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NGINX_CONF="${SCRIPT_DIR}/nginx/conf/nginx.conf"
COMPOSE_YML="${SCRIPT_DIR}/docker-compose.yml"
TS="$(date +%Y%m%d%H%M%S)"

if [[ ! -d "${SCRIPT_DIR}/nginx/conf" ]]; then
  echo "错误：未找到 ${SCRIPT_DIR}/nginx/conf，请从仓库中的 docker/ 目录运行本脚本。" >&2
  exit 1
fi

if [[ -f "$NGINX_CONF" ]]; then
  cp -a "$NGINX_CONF" "${NGINX_CONF}.bak.${TS}"
fi
if [[ -f "$COMPOSE_YML" ]]; then
  cp -a "$COMPOSE_YML" "${COMPOSE_YML}.bak.${TS}"
fi

cat >"$NGINX_CONF" <<'NGINX_EOF'
worker_processes  1;

events {
    worker_connections  1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    sendfile        on;
    keepalive_timeout  65;

    server {
        listen       80;
        server_name  _;

        location / {
            root   /usr/share/nginx/html;
            index  index.html index.htm;
        }

        # 与 Gateway 同宿主机：必须用 host 网络，直连 127.0.0.1:10010（禁止写 host.docker.internal，启动期解析易炸）
        location /api/ {
            proxy_pass http://127.0.0.1:10010/;
            proxy_connect_timeout 15s;
            proxy_send_timeout 120s;
            proxy_read_timeout 120s;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
NGINX_EOF

cat >"$COMPOSE_YML" <<'COMPOSE_EOF'
version: "3.3"

services:
  # MySQL 5.7
  mysql:
    image: mysql:5.7
    container_name: intellrecipe-mysql
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      TZ: Asia/Shanghai
    ports:
      - "3307:3306" # 映射宿主机3307端口到容器3306，符合application.yml配置
    volumes:
      - ./mysql/data:/var/lib/mysql
      - ./mysql/conf:/etc/mysql/conf.d
      - ./mysql/init:/docker-entrypoint-initdb.d
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

  # Redis 6.2
  redis:
    image: redis:6.2
    container_name: intellrecipe-redis
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - ./redis/data:/data
      - ./redis/conf/redis.conf:/etc/redis/redis.conf
    # 启动命令：使用配置文件并设置密码
    command: redis-server /etc/redis/redis.conf --requirepass ${REDIS_PASSWORD} --appendonly yes

  # Elasticsearch 7.17.24
  elasticsearch:
    image: elasticsearch:7.17.24
    container_name: intellrecipe-es
    restart: always
    environment:
      - "discovery.type=single-node"
      - "ES_JAVA_OPTS=${ES_JAVA_OPTS}"
      - "TZ=Asia/Shanghai"
    ports:
      - "9200:9200"
      - "9300:9300"
    volumes:
      - ./es/data:/usr/share/elasticsearch/data
      # - ./es/plugins:/usr/share/elasticsearch/plugins

  # Kibana 7.17.24 (Temporarily disabled to save memory)
  # kibana:
  #   image: kibana:7.17.24
  #   container_name: intellrecipe-kibana
  #   restart: always
  #   environment:
  #     - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
  #     - TZ=Asia/Shanghai
  #   ports:
  #     - "5601:5601"
  #   depends_on:
  #     - elasticsearch

  # Nacos 2.2.0
  nacos:
    image: nacos/nacos-server:v2.2.0
    container_name: intellrecipe-nacos
    environment:
      - MODE=standalone
    ports:
      - "8848:8848"
      - "9848:9848"
    restart: always

  # RabbitMQ 3.13-management
  rabbitmq:
    image: rabbitmq:3.13-management
    container_name: intellrecipe-rabbitmq
    restart: always
    environment:
      - RABBITMQ_DEFAULT_USER=${RABBITMQ_DEFAULT_USER}
      - RABBITMQ_DEFAULT_PASS=${RABBITMQ_DEFAULT_PASS}
      - TZ=Asia/Shanghai
    ports:
      - "5672:5672"
      - "15672:15672"
    volumes:
      - ./rabbitmq/data:/var/lib/rabbitmq

  # Nginx：host 网络，反代本机 127.0.0.1:10010（Gateway）。勿用 bridge+host.docker.internal，易启动解析失败反复 Restarting。
  nginx:
    image: nginx:1.18.0
    container_name: intellrecipe-nginx
    restart: always
    network_mode: host
    volumes:
      - ./nginx/html:/usr/share/nginx/html
      - ./nginx/conf/nginx.conf:/etc/nginx/nginx.conf
    depends_on:
      - mysql
      - redis
COMPOSE_EOF

if grep -q 'host.docker.internal' "$NGINX_CONF" 2>/dev/null; then
  echo "内部错误：生成的 nginx.conf 仍含 host.docker.internal" >&2
  exit 1
fi
if ! grep -q '127.0.0.1:10010' "$NGINX_CONF"; then
  echo "内部错误：生成的 nginx.conf 未包含 127.0.0.1:10010" >&2
  exit 1
fi
if ! grep -q 'network_mode: host' "$COMPOSE_YML"; then
  echo "内部错误：生成的 docker-compose.yml 未包含 nginx 的 network_mode: host" >&2
  exit 1
fi

cd "$SCRIPT_DIR"
if docker compose version >/dev/null 2>&1; then
  DC=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  DC=(docker-compose)
else
  echo "未找到 docker compose 或 docker-compose，请安装 Docker Compose 插件。" >&2
  exit 1
fi

echo "已写入 ${NGINX_CONF} 与 ${COMPOSE_YML}（原文件已备份为 *.bak.${TS}）"
echo "正在强制重建 nginx 容器…"
"${DC[@]}" up -d --force-recreate nginx

echo "完成。请检查："
echo "  docker ps   # nginx 不应再显示 0.0.0.0:80->80（host 网络无端口映射）"
echo "  docker logs intellrecipe-nginx --tail 20"
