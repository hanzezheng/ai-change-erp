from __future__ import annotations

import re
import uuid
from typing import Any

from app.gateway.model_gateway import ModelGateway
from app.schemas.action import (
    Ambiguity,
    AmbiguityCandidate,
    CandidateCustomer,
    CandidateProduct,
    ParseActionRequest,
    ParseActionResponse,
)


def _norm(text: str) -> str:
    return re.sub(r"\s+", "", text or "").lower()


def _match_customer(
    expression: str,
    candidates: list[CandidateCustomer],
) -> tuple[CandidateCustomer | None, list[CandidateCustomer]]:
    expr = _norm(expression)
    if not expr:
        return None, []
    hits: list[CandidateCustomer] = []
    for c in candidates:
        names = [_norm(c.customerName), *(_norm(a) for a in c.aliases)]
        if any(expr in n or n in expr for n in names if n):
            hits.append(c)
    if len(hits) == 1:
        return hits[0], hits
    return None, hits


def _match_product(
    expression: str,
    candidates: list[CandidateProduct],
) -> tuple[CandidateProduct | None, list[CandidateProduct]]:
    expr = _norm(expression)
    if not expr:
        return None, []
    # 农批常见简称
    alias_map = {
        "八零": "80果",
        "80": "80果",
        "粉蕉": "粉蕉",
    }
    for k, v in alias_map.items():
        if k in expr:
            expr = expr.replace(k, v)

    hits: list[CandidateProduct] = []
    for p in candidates:
        bag = [
            _norm(p.itemCode),
            _norm(p.productName or ""),
            _norm(p.spec or ""),
            *(_norm(a) for a in p.aliases),
        ]
        if any(expr in b or b in expr for b in bag if b):
            hits.append(p)
            continue
        # 「80果」命中 APPLE-80
        if "80果" in expr and ("80" in _norm(p.itemCode) or "80果" in _norm(p.spec or "")):
            hits.append(p)
        elif "粉蕉" in expr and "粉蕉" in _norm(p.productName or ""):
            hits.append(p)
    # 去重
    uniq: dict[str, CandidateProduct] = {h.itemCode: h for h in hits}
    hits = list(uniq.values())
    if len(hits) == 1:
        return hits[0], hits
    return None, hits


_QTY_UOM = re.compile(
    r"(?P<name>[\u4e00-\u9fffA-Za-z0-9\-]+?)(?P<qty>\d+(?:\.\d+)?)(?P<uom>箱|件|斤|公斤|袋)"
)


def _parse_items_from_text(text: str) -> list[dict[str, Any]]:
    """从『老韩80果20箱粉蕉30件』类文本抽出 name/qty/uom 片段。"""
    raw = re.sub(r"[，,、\s]+", "", text)
    # 去掉常见客户前缀词后再抽商品（启发式）
    for prefix in ("老韩", "韩老板", "给他", "再加", "要"):
        if raw.startswith(prefix):
            raw = raw[len(prefix) :]
    items: list[dict[str, Any]] = []
    for m in _QTY_UOM.finditer(raw):
        items.append(
            {
                "expression": m.group("name"),
                "qty": float(m.group("qty")),
                "uom": m.group("uom"),
            }
        )
    return items


def _detect_customer_expression(text: str) -> str | None:
    raw = text.strip()
    for token in ("老韩", "韩老板", "韩兆亮", "韩照亮", "亮哥"):
        if token in raw:
            return token
    return None


def parse_action(req: ParseActionRequest, gateway: ModelGateway) -> ParseActionResponse:
    action_id = str(uuid.uuid4())
    text = (req.text or "").strip()
    asr_text = req.asrText
    page = (req.context.currentPage or "").upper()

    # --- update_current_order：苹果改30箱 ---
    m_upd = re.search(
        r"(?P<name>[\u4e00-\u9fffA-Za-z0-9\-]+?)改(?:成|为)?(?P<qty>\d+(?:\.\d+)?)(?P<uom>箱|件|斤|公斤|袋)",
        re.sub(r"\s+", "", text),
    )
    if m_upd and (page in {"ORDER_EDIT", "ORDER_DETAIL", ""} or req.context.currentItems):
        name = m_upd.group("name")
        qty = float(m_upd.group("qty"))
        uom = m_upd.group("uom")
        # 优先从当前 Draft 找
        current_hit = None
        for it in req.context.currentItems:
            bag = _norm("".join(filter(None, [it.productName, it.spec, it.itemCode])))
            if _norm(name) in bag or bag in _norm(name) or _norm(name) in _norm(it.productName or ""):
                current_hit = it
                break
        product, product_hits = _match_product(name, req.candidateProducts)
        item_code = (current_hit.itemCode if current_hit else None) or (product.itemCode if product else None)
        if not item_code and len(product_hits) > 1:
            return ParseActionResponse(
                actionId=action_id,
                actionType="UPDATE_CURRENT_ORDER",
                status="NEED_USER_INPUT",
                targetPage="ORDER_EDIT",
                ambiguities=[
                    Ambiguity(
                        field="item",
                        expression=name,
                        candidates=[
                            AmbiguityCandidate(
                                itemCode=p.itemCode,
                                name=p.productName,
                                spec=p.spec,
                            )
                            for p in product_hits
                        ],
                    )
                ],
                payload={"orderId": req.context.currentOrderId},
                asrText=asr_text,
                provider=gateway.provider_name,
                model=gateway.model_name,
            )
        if not item_code:
            return ParseActionResponse(
                actionId=action_id,
                status="FAILED",
                message="无法识别要修改的商品",
                asrText=asr_text,
                provider=gateway.provider_name,
                model=gateway.model_name,
            )
        return ParseActionResponse(
            actionId=action_id,
            actionType="UPDATE_CURRENT_ORDER",
            status="READY",
            targetPage="ORDER_EDIT",
            payload={
                "orderId": req.context.currentOrderId,
                "operations": [
                    {
                        "operation": "SET_QTY",
                        "itemCode": item_code,
                        "qty": qty,
                        "uom": uom,
                    }
                ],
            },
            asrText=asr_text,
            provider=gateway.provider_name,
            model=gateway.model_name,
        )

    # --- create_order / 追加：多商品 ---
    cust_expr = _detect_customer_expression(text)
    customer, cust_hits = _match_customer(cust_expr or "", req.candidateCustomers)
    if cust_expr and not customer and len(cust_hits) > 1:
        return ParseActionResponse(
            actionId=action_id,
            actionType="CREATE_ORDER",
            status="NEED_USER_INPUT",
            targetPage="ORDER_EDIT",
            ambiguities=[
                Ambiguity(
                    field="customer",
                    expression=cust_expr,
                    candidates=[
                        AmbiguityCandidate(customerId=c.customerId, name=c.customerName)
                        for c in cust_hits
                    ],
                )
            ],
            asrText=asr_text,
            provider=gateway.provider_name,
            model=gateway.model_name,
        )

    fragments = _parse_items_from_text(text)
    if not fragments and not cust_expr:
        # 留给未来 LLM；当前 stub 明确失败
        _ = gateway.complete_json(system="parse", user=text)
        return ParseActionResponse(
            actionId=action_id,
            status="FAILED",
            message="暂无法理解该指令，请改用文字明确客户与商品",
            asrText=asr_text,
            provider=gateway.provider_name,
            model=gateway.model_name,
        )

    resolved_items: list[dict[str, Any]] = []
    ambiguities: list[Ambiguity] = []
    for frag in fragments:
        product, hits = _match_product(frag["expression"], req.candidateProducts)
        if product:
            uom = frag["uom"]
            if product.allowedUoms and uom not in product.allowedUoms:
                ambiguities.append(
                    Ambiguity(
                        field="uom",
                        expression=f"{frag['expression']}{frag['qty']}{uom}",
                        candidates=[
                            AmbiguityCandidate(itemCode=product.itemCode, name=u)
                            for u in product.allowedUoms
                        ],
                    )
                )
                continue
            resolved_items.append(
                {
                    "itemCode": product.itemCode,
                    "productId": product.productId,
                    "productName": product.productName,
                    "spec": product.spec,
                    "qty": frag["qty"],
                    "uom": uom,
                }
            )
        elif len(hits) > 1:
            ambiguities.append(
                Ambiguity(
                    field="item",
                    expression=frag["expression"],
                    candidates=[
                        AmbiguityCandidate(itemCode=p.itemCode, name=p.productName, spec=p.spec)
                        for p in hits
                    ],
                )
            )
        else:
            ambiguities.append(
                Ambiguity(field="item", expression=frag["expression"], candidates=[])
            )

    if ambiguities:
        payload: dict[str, Any] = {"items": resolved_items}
        if customer:
            payload["customer"] = {
                "customerId": customer.customerId,
                "customerName": customer.customerName,
            }
        return ParseActionResponse(
            actionId=action_id,
            actionType="CREATE_ORDER" if page not in {"ORDER_EDIT"} else "UPDATE_CURRENT_ORDER",
            status="NEED_USER_INPUT",
            targetPage="ORDER_EDIT",
            ambiguities=ambiguities,
            payload=payload,
            asrText=asr_text,
            provider=gateway.provider_name,
            model=gateway.model_name,
        )

    # 订单编辑页且已有客户上下文 → 追加商品
    if page == "ORDER_EDIT" and req.context.currentCustomerId and resolved_items and not cust_expr:
        return ParseActionResponse(
            actionId=action_id,
            actionType="UPDATE_CURRENT_ORDER",
            status="READY",
            targetPage="ORDER_EDIT",
            payload={
                "orderId": req.context.currentOrderId,
                "operations": [
                    {
                        "operation": "ADD_ITEM",
                        "itemCode": it["itemCode"],
                        "productId": it.get("productId"),
                        "productName": it.get("productName"),
                        "spec": it.get("spec"),
                        "qty": it["qty"],
                        "uom": it["uom"],
                    }
                    for it in resolved_items
                ],
            },
            asrText=asr_text,
            provider=gateway.provider_name,
            model=gateway.model_name,
        )

    if not customer and not req.context.currentCustomerId:
        return ParseActionResponse(
            actionId=action_id,
            actionType="CREATE_ORDER",
            status="NEED_USER_INPUT",
            targetPage="ORDER_EDIT",
            ambiguities=[
                Ambiguity(field="customer", expression=cust_expr or text, candidates=[])
            ],
            payload={"items": resolved_items},
            asrText=asr_text,
            provider=gateway.provider_name,
            model=gateway.model_name,
        )

    cust_id = customer.customerId if customer else req.context.currentCustomerId
    cust_name = customer.customerName if customer else req.context.currentCustomerName
    return ParseActionResponse(
        actionId=action_id,
        actionType="CREATE_ORDER",
        status="READY",
        targetPage="ORDER_EDIT",
        payload={
            "customer": {"customerId": cust_id, "customerName": cust_name},
            "items": resolved_items,
        },
        asrText=asr_text,
        provider=gateway.provider_name,
        model=gateway.model_name,
    )
