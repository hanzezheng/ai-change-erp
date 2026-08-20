#!/usr/bin/env bash
# WSL2 + Docker Desktop 本地开发一键引导（在 WSL 终端、仓库根目录执行）
#
#   bash scripts/wsl-dev.sh
#
# 可选环境变量：
#   ERP_CONTAINER   默认 erpnext-backend-1
#   SPRING_PORT     默认 18082（ERP 占 8080 时用此端口）
#   NONGPI_ENV_FILE 默认 ~/nongpi-local.env

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${NONGPI_ENV_FILE:-$HOME/nongpi-local.env}"
SPRING_PORT="${SPRING_PORT:-18082}"
ERP_CONTAINER="${ERP_CONTAINER:-erpnext-backend-1}"
JAR="$ROOT/backend/target/nongpi-backend-0.1.0-SNAPSHOT.jar"
LOG="/tmp/nongpi-${SPRING_PORT}.log"
PID="/tmp/nongpi-${SPRING_PORT}.pid"

step() { echo ""; echo ">>> $*"; }

if ! command -v docker >/dev/null; then
  echo "未找到 docker。请在 Windows 安装 Docker Desktop 并启用 WSL2 集成。" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$ERP_CONTAINER"; then
  echo "ERPNext 容器 $ERP_CONTAINER 未运行。" >&2
  echo "请先在 frappe_docker 目录执行: docker compose -f pwd.yml up -d" >&2
  exit 1
fi

step "1/4 ERPNext 种子数据 + API Key"
bash "$ROOT/scripts/erpnext/init-dev.sh" | tee /tmp/nongpi-erp-init.log

if [[ ! -f "$ENV_FILE" ]]; then
  step "创建 env 模板 $ENV_FILE"
  cat > "$ENV_FILE" <<'EOF'
export APP_JWT_SECRET='请改成至少32字符随机串'
export APP_CREDENTIAL_ENCRYPTION_KEY='请改成至少32字符随机串'
export DATABASE_URL=jdbc:postgresql://localhost:5432/nongpi
export DATABASE_USERNAME=nongpi
export DATABASE_PASSWORD=nongpi
export APP_BOOTSTRAP_LOGIN=boss
export APP_BOOTSTRAP_PASSWORD='请改成你的密码'
export APP_BOOTSTRAP_TENANT_NAME=农批测试档口
export ERP_BASE_URL=http://localhost:8080
export ERP_SITE_NAME=frontend
export ERP_DEFAULT_COMPANY=农批测试档口
export ERP_SELLING_PRICE_LIST=Standard Selling
export ERP_DEFAULT_WAREHOUSE='Stores - NPT'
export ERP_API_KEY=REPLACE_ME
export ERP_API_SECRET=REPLACE_ME
EOF
  echo "请编辑 $ENV_FILE 后重新运行本脚本。" >&2
  exit 1
fi

step "2/4 写入 ERP API Key（来自 init 日志）"
export ENV_FILE
python3 - <<'PY'
import re, pathlib, os
env_path = pathlib.Path(os.environ["ENV_FILE"])
log = pathlib.Path("/tmp/nongpi-erp-init.log").read_text()
key = re.search(r"APIKEY=(\S+)", log)
sec = re.search(r"APISECRET=(\S+)", log)
if not key or not sec:
    raise SystemExit("init 日志里未找到 APIKEY/APISECRET")
text = env_path.read_text()
text = re.sub(r"^export ERP_API_KEY=.*$", f"export ERP_API_KEY={key.group(1)}", text, flags=re.M)
text = re.sub(r"^export ERP_API_SECRET=.*$", f"export ERP_API_SECRET={sec.group(1)}", text, flags=re.M)
if "export ERP_DEFAULT_COMPANY=" not in text:
    text += "export ERP_DEFAULT_COMPANY=农批测试档口\n"
env_path.write_text(text)
print("已更新", env_path)
PY

step "3/4 构建并启动 Spring Boot :${SPRING_PORT}"
# shellcheck disable=SC1090
source "$ENV_FILE"
cd "$ROOT/backend"
./mvnw -q -DskipTests package
if [[ -f "$PID" ]] && kill -0 "$(cat "$PID")" 2>/dev/null; then
  echo "Spring 已在运行 pid=$(cat "$PID")"
else
  nohup java -jar "$JAR" \
    --spring.profiles.active=local \
    --server.port="$SPRING_PORT" \
    --logging.file.name="$LOG" \
    > "/tmp/nongpi-${SPRING_PORT}-stdout.log" 2>&1 &
  echo $! > "$PID"
  sleep 8
fi
curl -sf "http://127.0.0.1:${SPRING_PORT}/actuator/health" >/dev/null || {
  echo "Spring 启动失败，查看 $LOG" >&2
  exit 1
}

step "4/4 同步 ERP 连接 + 跑 API 黄金路径"
python3 - <<PY
import json, os, pathlib, urllib.request, urllib.error

def load_env(path):
    env = {}
    for line in pathlib.Path(path).read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        if line.startswith("export "):
            line = line[7:]
        k, _, v = line.partition("=")
        env[k.strip()] = v.strip().strip("'").strip('"')
    return env

base = f"http://127.0.0.1:{os.environ['SPRING_PORT']}"
env = load_env(os.environ["ENV_FILE"])

def req(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body else None
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(r, timeout=60) as resp:
        return resp.status, json.loads(resp.read().decode() or "null")

st, body = req("POST", "/api/v1/auth/login", {"login": env["APP_BOOTSTRAP_LOGIN"], "password": env["APP_BOOTSTRAP_PASSWORD"]})
token = body["accessToken"]
req("PUT", "/api/v1/erp-connection", {
    "baseUrl": env["ERP_BASE_URL"],
    "siteName": env.get("ERP_SITE_NAME", "frontend"),
    "apiKey": env["ERP_API_KEY"],
    "apiSecret": env["ERP_API_SECRET"],
    "defaultCompany": env.get("ERP_DEFAULT_COMPANY", "农批测试档口"),
    "sellingPriceList": env.get("ERP_SELLING_PRICE_LIST", "Standard Selling"),
    "defaultWarehouse": env.get("ERP_DEFAULT_WAREHOUSE", "Stores - NPT"),
}, token=token)
print("erp-connection 已同步")
PY

SPRING_BASE="http://127.0.0.1:${SPRING_PORT}" NONGPI_ENV_FILE="$ENV_FILE" \
  python3 "$ROOT/mobile/scripts/phase41_api_golden_path.py" | tail -3

echo ""
echo "=== 就绪 ==="
echo "Spring API:  http://127.0.0.1:${SPRING_PORT}"
echo "ERPNext UI:  http://localhost:8080  (或你映射的 8000)"
echo "Flutter:     cd mobile && flutter run -d emulator-5554 --dart-define=API_BASE_URL=http://10.0.2.2:${SPRING_PORT}"
echo "Env 文件:    $ENV_FILE"
