#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"
mvn clean package -DskipTests

ls -lh \
  intellrecipe-gateway/target/*.jar \
  user-service/target/*.jar \
  item-service/target/*.jar \
  voucher-service/target/*.jar \
  diet-service/target/*.jar
