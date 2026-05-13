#!/bin/sh
# Resolve Docker default gateway (= host) and write upstream for Gateway :10010.
# Do NOT use "set -e" — ip/getent may return non-zero and must not abort the script.

GW=""

if command -v ip >/dev/null 2>&1; then
  GW=$(ip -4 route show default 2>/dev/null | awk '{print $3; exit}') || true
fi

if [ -z "$GW" ] && [ -r /proc/net/route ]; then
  # Little-endian hex gateway (3rd column) -> a.b.c.d
  GW=$(awk '$2 == "00000000" && $3 != "00000000" {
    g = $3
    printf "%d.%d.%d.%d\n",
      sprintf("%d", "0x" substr(g, 7, 2)),
      sprintf("%d", "0x" substr(g, 5, 2)),
      sprintf("%d", "0x" substr(g, 3, 2)),
      sprintf("%d", "0x" substr(g, 1, 2))
    exit
  }' /proc/net/route 2>/dev/null) || true
fi

if [ -z "$GW" ] && command -v getent >/dev/null 2>&1; then
  GW=$(getent ahostsv4 host.docker.internal 2>/dev/null | awk '{print $1; exit}') || true
fi

if [ -z "$GW" ]; then
  echo "intellrecipe-nginx: WARN could not detect host IP; using 172.17.0.1 (override if wrong)." >&2
  GW="172.17.0.1"
fi

cat > /etc/nginx/intellrecipe-gateway-upstream.conf <<EOF
upstream intellrecipe_gateway {
    server ${GW}:10010;
}
EOF

echo "intellrecipe-nginx: gateway upstream -> ${GW}:10010" >&2
