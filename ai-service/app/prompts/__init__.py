from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path

_PROMPTS_DIR = Path(__file__).resolve().parent


@lru_cache(maxsize=8)
def load_prompt(name: str) -> str:
    path = _PROMPTS_DIR / name
    return path.read_text(encoding="utf-8").strip()


def render_user_prompt(
    *,
    text: str,
    current_page: str | None,
    current_order_id: str | None,
    current_customer_id: str | None,
    current_customer_name: str | None,
    current_items: list[dict],
    candidate_customers: list[dict],
    candidate_products: list[dict],
) -> str:
    template = load_prompt("parse_action_user.txt")
    return (
        template.replace("{{text}}", text or "")
        .replace("{{currentPage}}", current_page or "")
        .replace("{{currentOrderId}}", current_order_id or "")
        .replace("{{currentCustomerId}}", current_customer_id or "")
        .replace("{{currentCustomerName}}", current_customer_name or "")
        .replace("{{currentItemsJson}}", json.dumps(current_items, ensure_ascii=False, indent=2))
        .replace(
            "{{candidateCustomersJson}}",
            json.dumps(candidate_customers, ensure_ascii=False, indent=2),
        )
        .replace(
            "{{candidateProductsJson}}",
            json.dumps(candidate_products, ensure_ascii=False, indent=2),
        )
    )
