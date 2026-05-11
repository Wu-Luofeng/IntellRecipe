#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_DIR="${PROJECT_ROOT}/docker"

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo "Docker Compose is not installed. Install docker compose plugin or docker-compose first."
  exit 1
fi

if [ ! -f "${DOCKER_DIR}/.env" ]; then
  cp "${SCRIPT_DIR}/docker.env.example" "${DOCKER_DIR}/.env"
  echo "Created ${DOCKER_DIR}/.env from deploy/docker.env.example. Review it before production use."
fi

mkdir -p \
  "${DOCKER_DIR}/mysql/data" \
  "${DOCKER_DIR}/redis/data" \
  "${DOCKER_DIR}/rabbitmq/data"

# Elasticsearch is heavy; skip by default on small VPS (e.g. 4GB RAM).
# To start it: START_ELASTICSEARCH=1 bash deploy/02-start-infra.sh
if [ "${START_ELASTICSEARCH:-0}" = "1" ]; then
  mkdir -p "${DOCKER_DIR}/es/data"
  chmod 0777 "${DOCKER_DIR}/es/data"
fi

cd "${DOCKER_DIR}"
if [ "${START_ELASTICSEARCH:-0}" = "1" ]; then
  "${COMPOSE[@]}" up -d mysql redis nacos rabbitmq elasticsearch nginx
else
  "${COMPOSE[@]}" up -d mysql redis nacos rabbitmq nginx
fi
"${COMPOSE[@]}" ps
