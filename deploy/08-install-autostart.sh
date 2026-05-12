#!/usr/bin/env bash
# One-time: register systemd so IntellRecipe comes up after reboot without typing anything.
# Run from the repo on the server:  sudo bash deploy/08-install-autostart.sh
# Remove:                              sudo bash deploy/08-install-autostart.sh uninstall
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
UNIT="/etc/systemd/system/intellrecipe-after-boot.service"
NAME="intellrecipe-after-boot.service"

if [ "${1:-}" = "uninstall" ]; then
  if [ "$(id -u)" -ne 0 ]; then
    echo "Run uninstall with sudo."
    exit 1
  fi
  systemctl disable "${NAME}" 2>/dev/null || true
  systemctl stop "${NAME}" 2>/dev/null || true
  rm -f "${UNIT}"
  systemctl daemon-reload
  echo "Removed ${NAME}."
  exit 0
fi

if [ "$(id -u)" -ne 0 ]; then
  echo "This must be run once with sudo, e.g.:  sudo bash deploy/08-install-autostart.sh"
  exit 1
fi

RUN_AS="${RUN_AS:-${SUDO_USER:-}}"
if [ -z "${RUN_AS}" ] || [ "${RUN_AS}" = "root" ]; then
  RUN_AS="$(logname 2>/dev/null || true)"
fi
if [ -z "${RUN_AS}" ] || [ "${RUN_AS}" = "root" ]; then
  RUN_AS=ubuntu
fi
if [ "${RUN_AS}" = "root" ]; then
  echo "Cannot run services as root. Re-run as:  sudo RUN_AS=你的登录用户名 bash deploy/08-install-autostart.sh"
  exit 1
fi

if ! id "${RUN_AS}" >/dev/null 2>&1; then
  echo "ERROR: user '${RUN_AS}' does not exist. Export RUN_AS=your_login before sudo."
  exit 1
fi

POST_REBOOT="${SCRIPT_DIR}/07-post-reboot.sh"
if [ ! -f "${POST_REBOOT}" ]; then
  echo "ERROR: missing ${POST_REBOOT}. Copy deploy/07-post-reboot.sh onto this server or git pull, then re-run."
  exit 1
fi

chmod +x "${POST_REBOOT}"

cat > "${UNIT}" <<EOF
[Unit]
Description=IntellRecipe: Docker middleware + Spring Boot after boot
After=network-online.target docker.service
Wants=docker.service

[Service]
Type=oneshot
User=${RUN_AS}
Group=${RUN_AS}
WorkingDirectory=${PROJECT_ROOT}
ExecStart=/bin/bash ${POST_REBOOT}
TimeoutStartSec=0
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable "${NAME}"
echo "Enabled ${NAME} (runs as user ${RUN_AS})."
echo "View last run:  sudo journalctl -u ${NAME} -b --no-pager"
echo "Test now (optional):  sudo systemctl start ${NAME}"
