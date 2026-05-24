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
      > "${LOG_DIR}/${module}.log" 2>&1 &
  else
    nohup java ${JAVA_OPTS:-} -jar "${jar}" > "${LOG_DIR}/${module}.log" 2>&1 &
  fi
  echo "$!" > "${pid_file}"
  echo "Started ${module}, pid $(cat "${pid_file}"), log ${LOG_DIR}/${module}.log"
}

start_service user-service
start_service item-service
start_service voucher-service
start_service diet-service
start_service intellrecipe-gateway
