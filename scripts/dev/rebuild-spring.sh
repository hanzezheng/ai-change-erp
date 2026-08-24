#!/usr/bin/env bash
# 重建并重启本机 Spring（WSL），依赖 ~/nongpi-local.env 与 AI :8090
set -euo pipefail
ROOT=/mnt/e/ai-new-erp/ai-change-erp
set +u
# shellcheck disable=SC1090
source "$HOME/nongpi-local.env"
set -u
export AI_SERVICE_BASE_URL="${AI_SERVICE_BASE_URL:-http://127.0.0.1:8090}"

echo "== stop Spring =="
if [[ -f /tmp/nongpi-8080.pid ]]; then
  kill "$(cat /tmp/nongpi-8080.pid)" 2>/dev/null || true
fi
fuser -k 8080/tcp 2>/dev/null || true
sleep 2

echo "== rebuild =="
cd "$ROOT/backend"
sed -i 's/\r$//' mvnw || true
chmod +x mvnw
./mvnw -q clean -DskipTests package
JAR="$ROOT/backend/target/nongpi-backend-0.1.0-SNAPSHOT.jar"

echo "== start Spring =="
nohup bash -lc "set +u; source \$HOME/nongpi-local.env; set -u; export AI_SERVICE_BASE_URL=\${AI_SERVICE_BASE_URL:-http://127.0.0.1:8090}; exec java -jar '$JAR' --spring.profiles.active=local --server.port=8080 --logging.file.name=/tmp/nongpi-8080.log" \
  > /tmp/nongpi-8080-stdout.log 2>&1 &
echo $! > /tmp/nongpi-8080.pid

for i in $(seq 1 60); do
  if curl -sf http://127.0.0.1:8080/actuator/health | grep -q UP; then
    echo SPRING_UP
    break
  fi
  sleep 2
done
curl -sf http://127.0.0.1:8080/actuator/health; echo
