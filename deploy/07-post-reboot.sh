#!/usr/bin/env bash
# Run after the host reboots (OOM kill, power cycle, etc.).
# Order: Docker -> middleware (no ES) -> Rabbit user alignment -> Spring Boot jars -> verify.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${SCRIPT_DIR}/env.sh"
DOCKER_COMPOSE="${PROJECT_ROOT}/docker/docker-compose.yml"

cd "${PROJECT_ROOT}"

echo "==> [1/6] Docker daemon"
if ! docker info >/dev/null 2>&1; then
  if systemctl start docker 2>/dev/null; then
    echo "Started docker via systemctl."
  elif command -v sudo >/dev/null 2>&1 && sudo -n systemctl start docker 2>/dev/null; then
    echo "Started docker via sudo systemctl."
  else
    echo "ERROR: Docker is not running and could not be started. Run as root or with sudo:"
    echo "  sudo systemctl start docker"
    exit 1
  fi
fi
docker info >/dev/null
echo "Docker OK."

echo "==> [2/6] docker-compose.yml version (old docker-compose v1)"
if [ -f "${DOCKER_COMPOSE}" ] && ! head -n1 "${DOCKER_COMPOSE}" | grep -q '^version:'; then
  tmp="${DOCKER_COMPOSE}.new.$$"
  { printf '%s\n\n' 'version: "3.3"'; cat "${DOCKER_COMPOSE}"; } > "${tmp}"
  mv "${tmp}" "${DOCKER_COMPOSE}"
  echo "Prepended version: \"3.3\" to docker-compose.yml."
fi

echo "==> [3/6] Middleware (MySQL, Redis, Nacos, RabbitMQ, Nginx; Elasticsearch skipped)"
# Same default as 02-start-infra.sh: do not start ES on small RAM.
START_ELASTICSEARCH=0 bash "${SCRIPT_DIR}/02-start-infra.sh"

echo "Waiting for MySQL / Nacos / RabbitMQ to accept connections (25s)..."
sleep 25

echo "==> [4/6] RabbitMQ user must match deploy/env.sh (broker ignores .env after first init)"
if [ -f "${ENV_FILE}" ]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi
RABBIT_USER="${RABBITMQ_USERNAME:-root}"
RABBIT_PASS="${RABBITMQ_PASSWORD:-123456}"
if docker exec intellrecipe-rabbitmq rabbitmqctl authenticate_user "${RABBIT_USER}" "${RABBIT_PASS}" >/dev/null 2>&1; then
  echo "RabbitMQ user '${RABBIT_USER}' authenticates OK."
else
  echo "Creating/updating RabbitMQ user '${RABBIT_USER}' (password from env.sh)..."
  docker exec intellrecipe-rabbitmq rabbitmqctl add_user "${RABBIT_USER}" "${RABBIT_PASS}" 2>/dev/null || \
    docker exec intellrecipe-rabbitmq rabbitmqctl change_password "${RABBIT_USER}" "${RABBIT_PASS}"
  docker exec intellrecipe-rabbitmq rabbitmqctl set_permissions -p / "${RABBIT_USER}" ".*" ".*" ".*"
  docker exec intellrecipe-rabbitmq rabbitmqctl set_user_tags "${RABBIT_USER}" administrator
  echo "RabbitMQ user '${RABBIT_USER}' ready."
fi

echo "==> [5/6] env.sh sanity (OOM prevention on 4GB)"
if [ ! -f "${ENV_FILE}" ]; then
  echo "WARNING: ${ENV_FILE} missing. Copy deploy/env.example and edit, then re-run this script."
else
  if grep -qE 'ITEM_ELASTICSEARCH_ENABLED=(true|1)' "${ENV_FILE}" 2>/dev/null; then
    echo "WARNING: ITEM_ELASTICSEARCH_ENABLED should be false when Elasticsearch is not running."
  fi
  if grep -qE -- '-Xmx(5[12][0-9]|[6-9][0-9][0-9])[mMgG]' "${ENV_FILE}" 2>/dev/null; then
    echo "WARNING: JAVA_OPTS uses -Xmx512m or higher per process. On 4GB RAM this often causes OOM."
    echo "         Prefer something like: export JAVA_OPTS=\"-Xms128m -Xmx256m -Duser.timezone=Asia/Shanghai\""
  fi
fi

bash "${SCRIPT_DIR}/05-stop-services.sh" || true
bash "${SCRIPT_DIR}/04-start-services.sh"

echo "==> [6/6] Verify"
bash "${SCRIPT_DIR}/06-verify.sh"

echo
echo "Post-reboot flow finished. If a port is missing, tail logs/ under the project root."
echo "Memory: run  free -h  — if available RAM is low, lower JAVA_OPTS or add swap before next spike."
