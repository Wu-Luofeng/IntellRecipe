#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${SCRIPT_DIR}/env.sh"
LOG_DIR="${PROJECT_ROOT}/logs"
PID_DIR="${PROJECT_ROOT}/deploy/pids"

if [ ! -f "${ENV_FILE}" ]; then
  cp "${SCRIPT_DIR}/env.example" "${ENV_FILE}"
  echo "Created ${ENV_FILE} from deploy/env.example. Review it before production use."
fi

# shellcheck disable=SC1090
source "${ENV_FILE}"

mkdir -p "${LOG_DIR}" "${PID_DIR}"

# 日志由各服务 logback 的 file appender 直接写入（带大小/天数轮转，总量上限 1GB），
# 这里只负责告诉它写到哪个目录。
# 【不要再用 nohup 把 stdout 重定向到日志文件】，原因有三：
#   1. 会与 logback 的文件写入抢同一个文件，内容互相错乱；
#   2. Spring Boot 的 console appender 会重复输出一份，文件双倍增长；
#   3. 没有轮转机制 —— 历史上就是这样堆出过一个 5.4GB 的日志文件。
# 所以 stdout 直接丢弃。要看日志请打开 ${LOG_DIR}/<module>.log。
export LOG_PATH="${LOG_DIR}"

start_service() {
  local module="$1"
  local jar
  local pid_file="${PID_DIR}/${module}.pid"
  jar="$(ls "${PROJECT_ROOT}/${module}/target/${module}"-*.jar 2>/dev/null | grep -v 'original-' | head -n 1 || true)"

  if [ -z "${jar}" ]; then
    echo "Jar not found for ${module}. Run deploy/03-build-services.sh first."
    exit 1
  fi

  if [ -f "${pid_file}" ] && kill -0 "$(cat "${pid_file}")" >/dev/null 2>&1; then
    echo "${module} is already running with pid $(cat "${pid_file}")"
    return
  fi

  # Command-line args beat env / application.yml. Ensures ES repos stay off when ES container is not used.
  if [ "${module}" = "item-service" ] && [ "${ITEM_ELASTICSEARCH_ENABLED:-true}" = "false" ]; then
    nohup java ${JAVA_OPTS:-} -jar "${jar}" \
      --spring.data.elasticsearch.repositories.enabled=false \
      --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration,org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration \
      > /dev/null 2>&1 &
  else
    nohup java ${JAVA_OPTS:-} -jar "${jar}" > /dev/null 2>&1 &
  fi
  echo "$!" > "${pid_file}"
  echo "Started ${module}, pid $(cat "${pid_file}"), log ${LOG_DIR}/${module}.log"
}

start_service user-service
start_service item-service
start_service voucher-service
start_service diet-service
start_service intellrecipe-gateway
