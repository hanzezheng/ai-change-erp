#!/usr/bin/env bash
# 启动 / 重启 ai-service :8090
set -euo pipefail
ROOT=/mnt/e/ai-new-erp/ai-change-erp
cd "$ROOT/ai-service"
if [[ ! -x .venv/bin/uvicorn ]]; then
  python3 -m venv .venv
  .venv/bin/pip install -q -r requirements.txt
fi
fuser -k 8090/tcp 2>/dev/null || true
sleep 1
nohup .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8090 > /tmp/nongpi-ai-8090.log 2>&1 &
echo $! > /tmp/nongpi-ai-8090.pid
for i in $(seq 1 40); do
  if curl -sf http://127.0.0.1:8090/health >/dev/null; then
    curl -s http://127.0.0.1:8090/health; echo
    exit 0
  fi
  sleep 0.5
done
echo "AI failed to start" >&2
tail -40 /tmp/nongpi-ai-8090.log >&2 || true
exit 1
