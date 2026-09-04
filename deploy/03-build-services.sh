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
for module in diet-service intellrecipe-gateway item-service recipe-service user-service voucher-service; do
  yml="${module}/src/main/resources/application.yml"
  if [ ! -f "${yml}" ]; then
    cp "${yml%.yml}.example.yml" "${yml}"
    echo "Restored ${yml} from application.example.yml"
  fi
done

mvn clean package -DskipTests

ls -lh \
  intellrecipe-gateway/target/*.jar \
  user-service/target/*.jar \
  item-service/target/*.jar \
  voucher-service/target/*.jar \
  diet-service/target/*.jar
