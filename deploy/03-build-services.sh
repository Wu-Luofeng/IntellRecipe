#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

# --- Local-dev friendly build ---
# src/main/resources/application.yml is git-ignored (each machine keeps its own
# local copy). On the cloud server a `git pull` after the yml files were removed
# from the index would delete them; rebuild them from the tracked templates so
# the jars still embed the default configuration (env.sh injects real values at
# runtime anyway).
# 模板更新时（例如本次新增 logging.file.name 与日志轮转配置），已存在的
# application.yml 也必须跟着更新，否则新配置永远进不了 jar —— 所以这里
# 用 cmp 比对，有差异就覆盖（旧文件保留为 .bak，可随时回滚）。
for module in diet-service intellrecipe-gateway item-service recipe-service user-service voucher-service; do
  yml="${module}/src/main/resources/application.yml"
  template="${yml%.yml}.example.yml"
  if [ ! -f "${yml}" ]; then
    cp "${template}" "${yml}"
    echo "Restored ${yml} from application.example.yml"
  elif ! cmp -s "${template}" "${yml}"; then
    cp "${yml}" "${yml}.bak"
    cp "${template}" "${yml}"
    echo "Updated ${yml} from application.example.yml (previous version kept as ${yml}.bak)"
  fi
done

mvn clean package -DskipTests

ls -lh \
  intellrecipe-gateway/target/*.jar \
  user-service/target/*.jar \
  item-service/target/*.jar \
  voucher-service/target/*.jar \
  diet-service/target/*.jar
