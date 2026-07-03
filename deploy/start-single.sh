#!/usr/bin/env bash
# 启动单个服务（供 deploy_one_click.bat 调用）
# 用法: bash deploy/start-single.sh <module>
# 例如: bash deploy/start-single.sh item-service

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${SCRIPT_DIR}/env.sh"
LOG_DIR="${PROJECT_ROOT}/logs"
PID_DIR="${SCRIPT_DIR}/pids"

MODULE="${1:-}"
if [ -z "${MODULE}" ]; then
  echo "用法: bash deploy/start-single.sh <module>"
  echo "例如: bash deploy/start-single.sh item-service"
  exit 1
fi

# 加载环境变量
if [ -f "${ENV_FILE}" ]; then
  source "${ENV_FILE}"
fi

mkdir -p "${LOG_DIR}" "${PID_DIR}"

JAR="$(ls "${PROJECT_ROOT}/${MODULE}/target/${MODULE}"-*.jar 2>/dev/null | grep -v 'original-' | head -n 1 || true)"

if [ -z "${JAR}" ]; then
  echo "错误: 找不到 ${MODULE} 的 jar 包，请先构建"
  exit 1
fi

PID_FILE="${PID_DIR}/${MODULE}.pid"

# 检查是否已在运行
if [ -f "${PID_FILE}" ] && kill -0 "$(cat "${PID_FILE}")" >/dev/null 2>&1; then
  echo "${MODULE} 已在运行 (pid $(cat "${PID_FILE}"))，先停止..."
  kill "$(cat "${PID_FILE}")"
  sleep 3
  rm -f "${PID_FILE}"
fi

# 确保端口已释放
sleep 1

# 启动服务
if [ "${MODULE}" = "item-service" ] && [ "${ITEM_ELASTICSEARCH_ENABLED:-true}" = "false" ]; then
  nohup java ${JAVA_OPTS:-} -jar "${JAR}" \
    --spring.data.elasticsearch.repositories.enabled=false \
    --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration,org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration \
    > "${LOG_DIR}/${MODULE}.log" 2>&1 &
else
  nohup java ${JAVA_OPTS:--Xms128m -Xmx256m} -Duser.timezone=Asia/Shanghai -jar "${JAR}" \
    > "${LOG_DIR}/${MODULE}.log" 2>&1 &
fi

echo "$!" > "${PID_FILE}"
echo "已启动 ${MODULE}, pid $(cat "${PID_FILE}"), 日志 ${LOG_DIR}/${MODULE}.log"

# 等待启动
sleep 5
if kill -0 "$(cat "${PID_FILE}")" >/dev/null 2>&1; then
  echo "✅ ${MODULE} 启动成功"
else
  echo "❌ ${MODULE} 启动失败，查看日志: tail -50 ${LOG_DIR}/${MODULE}.log"
  exit 1
fi