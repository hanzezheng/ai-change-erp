#!/usr/bin/env bash
# 一键初始化 ERPNext 开发数据（公司 + 黄金路径种子 + API Key）。
#
# 前提：frappe_docker 已 up，site 已创建（pwd.yml 的 create-site 跑完）。
#
# 用法（在 ai-change-erp 仓库根目录）：
#   bash scripts/erpnext/init-dev.sh
#
# 可选：ERP_CONTAINER=xxx ERP_SITE=frontend

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$DIR/lib.sh"

SITE="${ERP_SITE:-frontend}"
CONTAINER="$(erp_detect_backend_container)" || exit 1
echo "使用 ERPNext 容器: $CONTAINER (site=$SITE)"

run_console() {
  local script="$1"
  local remote="/tmp/nongpi-$(basename "$script")"
  echo ""
  echo "==> $(basename "$script")"
  # Windows 工作区可能是 CRLF；不要把脚本正文直接管道进 console（空行会拆块）
  tr -d '\r' < "$script" | docker exec -i "$CONTAINER" tee "$remote" >/dev/null
  # 在已连接 site 的 console 中整文件执行，并跳过脚本内二次 init/connect
  docker exec -i "$CONTAINER" bench --site "$SITE" console <<PY
path = "$remote"
src = open(path, encoding="utf-8").read().splitlines()
skip = ("frappe.init(", "frappe.connect(", "frappe.set_user(")
kept = [line for line in src if not any(line.strip().startswith(p) for p in skip)]
frappe.set_user("Administrator")
frappe.flags.in_import = True
exec(compile("\\n".join(kept) + "\\n", path, "exec"), {"frappe": frappe, "__name__": "__main__"})
print("SCRIPT_OK=" + path)
PY
}

company_count() {
  docker exec -i "$CONTAINER" bench --site "$SITE" console <<'PY' | tr -d '\r' | awk '/^[0-9]+$/{print; found=1} END{if(!found) print 0}'
import frappe
print(frappe.db.count("Company"))
PY
}

count="$(company_count)"
echo "Company count=$count"
if [[ "$count" == "0" ]]; then
  run_console "$DIR/setup_wizard.py"
else
  echo "==> setup_wizard 跳过（已有 Company）"
fi

run_console "$DIR/seed.py"
run_console "$DIR/set_primary_address.py"

echo ""
echo "==> mkkey（请复制到 env 文件）"
run_console "$DIR/mkkey.py"

echo ""
echo "完成。已创建：农批测试档口 / 韩兆亮 / APPLE-80 / BANANA-FEN / 库存与价格"
echo "Spring env 建议："
echo "  ERP_BASE_URL=http://localhost:8000"
echo "  ERP_SITE_NAME=$SITE"
echo "  ERP_DEFAULT_COMPANY=农批测试档口"
echo "  ERP_DEFAULT_WAREHOUSE=Stores - NPT"
echo "  ERP_SELLING_PRICE_LIST=Standard Selling"
