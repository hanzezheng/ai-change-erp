#!/usr/bin/env bash
# Phase 5 真栈冒烟：健康检查 + 文字开单 + 改当前单
set -euo pipefail
set +u
# shellcheck disable=SC1090
source "$HOME/nongpi-local.env"
set -u

WSL_IP=$(hostname -I | awk '{print $1}')
echo "WSL_IP=$WSL_IP"
echo -n "Spring: "; curl -sf http://127.0.0.1:8080/actuator/health; echo
echo -n "AI: "; curl -sf http://127.0.0.1:8090/health; echo
echo -n "ERP: "; curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8000/
docker exec nongpi-pg pg_isready -U nongpi

CODE=$(curl -s -o /tmp/login.json -w '%{http_code}' -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"login\":\"${APP_BOOTSTRAP_LOGIN}\",\"password\":\"${APP_BOOTSTRAP_PASSWORD}\"}")
echo "login_http=$CODE"
TOKEN=$(python3 -c 'import json; print(json.load(open("/tmp/login.json")).get("accessToken",""))')
test -n "$TOKEN"

python3 - "$TOKEN" <<'PY'
import json, sys, urllib.request

token = sys.argv[1]
base = "http://127.0.0.1:8080/api/v1"

def api(method, path, body=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        base + path,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(req) as r:
        return json.load(r)

# 候选是否到位
customers = api("GET", "/customers?page=1&pageSize=20")
content = customers.get("content") or []
print("customers", len(content), [c.get("customerName") for c in content[:5]])
products = api("GET", "/products/selector")
results = products.get("results") or []
print("products", len(results), [p.get("itemCode") for p in results[:8]])

create = api("POST", "/ai/actions", {
    "inputType": "TEXT",
    "text": "老韩80果20箱，粉蕉30件",
    "context": {"currentPage": "HOME", "currentItems": []},
})
print("create", {k: create.get(k) for k in ("status", "actionType", "targetPage", "message", "code")})
print("create.payload.customer", (create.get("payload") or {}).get("customer"))
print("create.payload.items", [
    {k: i.get(k) for k in ("itemCode", "qty", "uom")}
    for i in (create.get("payload") or {}).get("items") or []
])
assert create.get("status") == "READY", create
assert create.get("actionType") == "CREATE_ORDER", create
items = (create.get("payload") or {}).get("items") or []
assert [i["itemCode"] for i in items] == ["APPLE-80", "BANANA-FEN"], items

update = api("POST", "/ai/actions", {
    "inputType": "TEXT",
    "text": "苹果改30箱",
    "context": {
        "currentPage": "ORDER_EDIT",
        "currentCustomerId": create["payload"]["customer"]["customerId"],
        "currentCustomerName": create["payload"]["customer"]["customerName"],
        "currentItems": [
            {"itemCode": "APPLE-80", "productName": "苹果80果", "qty": 20, "uom": "箱"},
            {"itemCode": "BANANA-FEN", "productName": "香蕉粉蕉", "qty": 30, "uom": "件"},
        ],
    },
})
print("update", {k: update.get(k) for k in ("status", "actionType", "message", "code")})
print("update.ops", (update.get("payload") or {}).get("operations"))
assert update.get("status") == "READY", update
assert update.get("actionType") == "UPDATE_CURRENT_ORDER", update
ops = (update.get("payload") or {}).get("operations") or []
assert ops and ops[0]["itemCode"] == "APPLE-80" and ops[0]["qty"] == 30, ops
print("PHASE5_SMOKE_OK")
PY

echo "Flutter: cd /d E:\\ai-new-erp\\ai-change-erp\\mobile && flutter run -d chrome --dart-define=API_BASE_URL=http://${WSL_IP}:8080"
