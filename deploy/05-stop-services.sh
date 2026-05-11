#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_DIR="${SCRIPT_DIR}/pids"

for module in intellrecipe-gateway voucher-service item-service user-service; do
  pid_file="${PID_DIR}/${module}.pid"
  if [ ! -f "${pid_file}" ]; then
    echo "${module} is not tracked"
    continue
  fi

  pid="$(cat "${pid_file}")"
  if kill -0 "${pid}" >/dev/null 2>&1; then
    kill "${pid}"
    echo "Stopped ${module}, pid ${pid}"
  else
    echo "${module} pid ${pid} is not running"
  fi
  rm -f "${pid_file}"
done
