#!/bin/sh
# Resolve Docker bridge default gateway (= host IP reachable from container) and
# write upstream for Spring Cloud Gateway on host :10010. Avoids nginx startup
# failure when "host.docker.internal" is missing from /etc/hosts.
set -e
GW=""
if command -v ip >/dev/null 2>&1; then
  GW="$(ip -4 route show default 2>/dev/null | awk '{print $3; exit}')"
fi
if [ -z "$GW" ] && command -v getent >/dev/null 2>&1; then
  GW="$(getent ahostsv4 host.docker.internal 2>/dev/null | awk '{print $1; exit}')"
fi
if [ -z "$GW" ]; then
  echo "intellrecipe-nginx: could not resolve Docker host IP (ip route / host.docker.internal)." >&2
  exit 1
fi
cat > /etc/nginx/intellrecipe-gateway-upstream.conf <<EOF
upstream intellrecipe_gateway {
    server ${GW}:10010;
}
EOF
echo "intellrecipe-nginx: gateway upstream -> ${GW}:10010"
