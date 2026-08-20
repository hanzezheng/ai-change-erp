#!/usr/bin/env python3
"""Phase 4.1 API golden path against live Spring Boot + ERPNext.

Reads credentials from an env file (never prints secrets). Writes a JSON report.

Usage:
  export SPRING_BASE=http://127.0.0.1:8080
  export NONGPI_ENV_FILE=/path/to/local.env   # must define APP_BOOTSTRAP_LOGIN/PASSWORD
  python3 mobile/scripts/phase41_api_golden_path.py
"""
from __future__ import annotations

import json
import os
import pathlib
import uuid
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("SPRING_BASE", "http://127.0.0.1:8080")
ENV_FILE = pathlib.Path(os.environ.get("NONGPI_ENV_FILE", "/tmp/nongpi-phase2-run.env"))
REPORT = pathlib.Path(
    os.environ.get(
        "GOLDEN_PATH_REPORT",
        str(pathlib.Path(__file__).resolve().parent.parent / "artifacts" / "phase41-golden-path-report.json"),
    )
)


def load_env(path: pathlib.Path) -> dict[str, str]:
    env: dict[str, str] = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        if line.startswith("export "):
            line = line[len("export ") :]
        key, _, value = line.partition("=")
        env[key.strip()] = value.strip().strip("'").strip('"')
    return env


def req(method: str, path: str, body=None, token=None, extra_headers=None, timeout=60):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if extra_headers:
        headers.update(extra_headers)
    request = urllib.request.Request(
        BASE + urllib.parse.quote(path, safe="/:?&=%"),
        data=data,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as resp:
            raw = resp.read().decode()
            parsed = json.loads(raw) if raw else None
            return resp.status, parsed, None
    except urllib.error.HTTPError as err:
        raw = err.read().decode()
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = {"raw": raw[:500]}
        return err.code, parsed, parsed.get("code") if isinstance(parsed, dict) else str(err.code)
    except Exception as err:
        return 0, {"error": type(err).__name__}, type(err).__name__


def redact(obj):
    if isinstance(obj, dict):
        out = {}
        for k, v in obj.items():
            lk = k.lower()
            if any(s in lk for s in ("token", "secret", "password", "authorization")):
                out[k] = "<redacted>"
            else:
                out[k] = redact(v)
        return out
    if isinstance(obj, list):
        return [redact(x) for x in obj]
    return obj


def main() -> None:
    if not ENV_FILE.is_file():
        raise SystemExit(f"Env file not found: {ENV_FILE}")

    env = load_env(ENV_FILE)
    login = env["APP_BOOTSTRAP_LOGIN"]
    password = env["APP_BOOTSTRAP_PASSWORD"]
    steps = []

    def record(name, ok, detail=None):
        steps.append({"step": name, "ok": bool(ok), "detail": redact(detail) if detail is not None else None})
        print(f"{'PASS' if ok else 'FAIL'}  {name}")

    status, body, _ = req("POST", "/api/v1/auth/login", {"login": login, "password": password})
    token = body.get("accessToken") if isinstance(body, dict) else None
    record(
        "login",
        status == 200 and bool(token),
        {
            "http": status,
            "tenantName": body.get("tenantName") if isinstance(body, dict) else None,
            "role": body.get("role") if isinstance(body, dict) else None,
        },
    )
    if not token:
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        REPORT.write_text(json.dumps({"base": BASE, "steps": steps}, ensure_ascii=False, indent=2))
        raise SystemExit(1)

    status, body, _ = req("GET", "/api/v1/customers?q=韩兆亮", token=token)
    customers = (body or {}).get("content") if isinstance(body, dict) else []
    han = next((c for c in customers if c.get("customerName") == "韩兆亮" or c.get("customerId") == "韩兆亮"), None)
    record("search_customer_han", status == 200 and han is not None, {"http": status, "customer": han})
    customer_id = han["customerId"] if han else "韩兆亮"

    status, body, _ = req("GET", "/api/v1/products/selector?q=APPLE-80", token=token)
    results = (body or {}).get("results") if isinstance(body, dict) else []
    apple = next((p for p in results if p.get("itemCode") == "APPLE-80"), None)
    record(
        "product_apple80",
        status == 200 and apple is not None,
        {
            "http": status,
            "itemCode": (apple or {}).get("itemCode"),
            "spec": (apple or {}).get("spec"),
            "uoms": [u.get("uom") for u in (apple or {}).get("allowedUoms") or []],
        },
    )

    status, body, _ = req("GET", "/api/v1/products/selector?q=BANANA-FEN", token=token)
    results = (body or {}).get("results") if isinstance(body, dict) else []
    banana = next((p for p in results if p.get("itemCode") == "BANANA-FEN"), None)
    record("product_banana_fen", status == 200 and banana is not None, {"http": status, "itemCode": (banana or {}).get("itemCode")})

    def uom_of(variant, preferred):
        allowed = variant.get("allowedUoms") or []
        if any(u.get("uom") == preferred for u in allowed):
            return preferred
        return variant.get("defaultUom") or preferred

    def rate_of(variant, uom):
        for u in variant.get("allowedUoms") or []:
            if u.get("uom") == uom and u.get("referencePrice") is not None:
                return str(u.get("referencePrice"))
        if variant.get("referencePrice") is not None and (
            variant.get("priceUom") == uom or variant.get("defaultUom") == uom
        ):
            return str(variant.get("referencePrice"))
        return "68" if uom == "箱" else "30"

    apple_uom = uom_of(apple or {}, "箱")
    banana_uom = uom_of(banana or {}, "件")
    apple_rate = rate_of(apple or {}, apple_uom)
    banana_rate = rate_of(banana or {}, banana_uom)

    create_key = str(uuid.uuid4())
    status, body, code = req(
        "POST",
        "/api/v1/orders",
        {
            "customerId": customer_id,
            "items": [
                {"itemCode": "APPLE-80", "qty": "20", "uom": apple_uom, "rate": apple_rate},
                {"itemCode": "BANANA-FEN", "qty": "30", "uom": banana_uom, "rate": banana_rate},
            ],
        },
        token=token,
        extra_headers={"Idempotency-Key": create_key},
    )
    order = body if isinstance(body, dict) else {}
    order_id = order.get("orderId")
    record(
        "create_draft",
        status in (200, 201) and bool(order_id) and order.get("orderStatus") == "DRAFT",
        {
            "http": status,
            "code": code or order.get("code"),
            "orderId": order_id,
            "orderStatus": order.get("orderStatus"),
            "itemCount": len(order.get("items") or []),
            "totalAmount": order.get("totalAmount"),
        },
    )

    if order_id:
        items = []
        for item in order.get("items") or []:
            qty = "30" if item.get("itemCode") == "APPLE-80" else item.get("qty")
            items.append(
                {
                    "orderItemId": item.get("orderItemId"),
                    "itemCode": item.get("itemCode"),
                    "qty": qty,
                    "uom": item.get("uom"),
                    "rate": item.get("rate"),
                }
            )
        status, body, code = req(
            "PUT",
            f"/api/v1/orders/{urllib.parse.quote(order_id)}",
            {
                "customerId": customer_id,
                "transactionDate": order.get("transactionDate"),
                "expectedModifiedAt": order.get("updatedAt"),
                "items": items,
            },
            token=token,
        )
        order = body if isinstance(body, dict) else order
        apple_qty = next((i.get("qty") for i in (order.get("items") or []) if i.get("itemCode") == "APPLE-80"), None)
        record(
            "update_same_draft",
            status == 200 and order.get("orderId") == order_id and str(apple_qty) in ("30", "30.0", "30.00"),
            {"http": status, "orderId": order.get("orderId"), "appleQty": apple_qty},
        )

        status, body, code = req("POST", f"/api/v1/orders/{urllib.parse.quote(order_id)}/submit", token=token)
        order = body if isinstance(body, dict) else order
        record(
            "submit_same_order",
            status == 200 and order.get("orderStatus") == "SUBMITTED" and order.get("orderId") == order_id,
            {"http": status, "orderStatus": order.get("orderStatus"), "paymentStatus": order.get("paymentStatus")},
        )

        status, summary, _ = req("GET", f"/api/v1/orders/{urllib.parse.quote(order_id)}/payment-summary", token=token)
        remaining = (summary or {}).get("remainingToCollect") if isinstance(summary, dict) else None
        record("payment_summary_before", status == 200 and remaining is not None, {"http": status, "summary": summary})

        status, methods, _ = req("GET", "/api/v1/payment-methods", token=token)
        method_list = methods if isinstance(methods, list) else []
        method_id = method_list[0].get("paymentMethodId") if method_list else None
        record("payment_methods", status == 200 and bool(method_id), {"http": status, "count": len(method_list)})

        if method_id:
            pay_key = str(uuid.uuid4())
            status, pay, code = req(
                "POST",
                "/api/v1/payments",
                {
                    "customerId": customer_id,
                    "relatedOrderId": order_id,
                    "amount": "1000",
                    "paymentMethodId": method_id,
                },
                token=token,
                extra_headers={"Idempotency-Key": pay_key},
            )
            payment_id = (pay or {}).get("paymentId") if isinstance(pay, dict) else None
            record("create_payment_1000", status in (200, 201) and bool(payment_id), {"http": status, "paymentId": payment_id})

            if payment_id:
                status, pay, code = req("POST", f"/api/v1/payments/{urllib.parse.quote(payment_id)}/confirm", token=token)
                record(
                    "confirm_payment_1000",
                    status == 200 and (pay or {}).get("paymentStatus") == "CONFIRMED",
                    {"http": status, "paymentStatus": (pay or {}).get("paymentStatus")},
                )

            status, summary, _ = req("GET", f"/api/v1/orders/{urllib.parse.quote(order_id)}/payment-summary", token=token)
            pay_status = (summary or {}).get("paymentStatus") if isinstance(summary, dict) else None
            remaining = (summary or {}).get("remainingToCollect") if isinstance(summary, dict) else None
            record("partial_after_1000", status == 200 and pay_status == "PARTIAL", {"http": status, "summary": summary})

            if remaining is not None:
                pay_key2 = str(uuid.uuid4())
                status, pay2, code = req(
                    "POST",
                    "/api/v1/payments",
                    {
                        "customerId": customer_id,
                        "relatedOrderId": order_id,
                        "amount": str(remaining),
                        "paymentMethodId": method_id,
                    },
                    token=token,
                    extra_headers={"Idempotency-Key": pay_key2},
                )
                payment_id2 = (pay2 or {}).get("paymentId") if isinstance(pay2, dict) else None
                record("create_payment_remaining", status in (200, 201) and bool(payment_id2), {"http": status, "amount": remaining})
                if payment_id2:
                    status, pay2, code = req("POST", f"/api/v1/payments/{urllib.parse.quote(payment_id2)}/confirm", token=token)
                    record(
                        "confirm_payment_remaining",
                        status == 200 and (pay2 or {}).get("paymentStatus") == "CONFIRMED",
                        {"http": status},
                    )

            status, summary, _ = req("GET", f"/api/v1/orders/{urllib.parse.quote(order_id)}/payment-summary", token=token)
            status2, order2, _ = req("GET", f"/api/v1/orders/{urllib.parse.quote(order_id)}", token=token)
            record(
                "paid_but_order_not_completed",
                status == 200
                and (summary or {}).get("paymentStatus") == "PAID"
                and (order2 or {}).get("orderStatus") == "SUBMITTED",
                {"orderStatus": (order2 or {}).get("orderStatus"), "paymentStatus": (order2 or {}).get("paymentStatus")},
            )

            status, hist, _ = req("GET", f"/api/v1/payments?relatedOrderId={urllib.parse.quote(order_id)}", token=token)
            content = (hist or {}).get("content") if isinstance(hist, dict) else []
            record("payment_history_by_order", status == 200 and len(content) >= 2, {"http": status, "count": len(content)})

    status, inv, _ = req("GET", "/api/v1/inventory?q=APPLE-80", token=token)
    content = (inv or {}).get("content") if isinstance(inv, dict) else []
    record("inventory_apple80", status == 200, {"http": status, "count": len(content)})

    status, body, _ = req("POST", "/api/v1/auth/login", {"login": login, "password": password})
    refresh = body.get("refreshToken") if isinstance(body, dict) else None
    access = body.get("accessToken") if isinstance(body, dict) else token
    status, _, code = req("POST", "/api/v1/auth/logout", {"refreshToken": refresh}, token=access)
    record("logout", status in (200, 204), {"http": status, "code": code})

    passed = sum(1 for s in steps if s["ok"])
    failed = [s["step"] for s in steps if not s["ok"]]
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(
        json.dumps({"base": BASE, "passed": passed, "total": len(steps), "failed": failed, "steps": steps}, ensure_ascii=False, indent=2)
    )
    print(f"RESULT {passed}/{len(steps)}")
    if failed:
        print("FAILED:", ", ".join(failed))
        raise SystemExit(1)


if __name__ == "__main__":
    main()
