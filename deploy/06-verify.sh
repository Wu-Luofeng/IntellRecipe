#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "Docker containers (docker ps; avoids docker-compose ps hang):"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | head -n 30

echo
echo "Spring Boot process ports:"
for port in 8081 8082 8083 10010; do
  if command -v ss >/dev/null 2>&1; then
    ss -lntp | grep ":${port} " || true
  else
    netstat -lntp 2>/dev/null | grep ":${port} " || true
  fi
done

echo
echo "HTTP checks:"
curl -fsS http://127.0.0.1:8848/nacos/ >/dev/null && echo "Nacos reachable"
curl -fsS http://127.0.0.1:9200/ >/dev/null && echo "Elasticsearch reachable"
curl -fsS http://127.0.0.1:10010/ >/dev/null && echo "Gateway reachable" || echo "Gateway root may not expose a route"
curl -fsS http://127.0.0.1/ >/dev/null && echo "Nginx reachable"

echo
echo "If Nacos shows user-service, item-service, voucher-service, and intellrecipe-gateway as healthy, deployment is connected."
