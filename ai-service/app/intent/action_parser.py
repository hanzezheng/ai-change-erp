from __future__ import annotations

import uuid
from typing import Any

from app.gateway.model_gateway import ModelGateway, StubModelGateway
from app.prompts import load_prompt, render_user_prompt
from app.schemas.action import (
    Ambiguity,
    AmbiguityCandidate,
    ParseActionRequest,
    ParseActionResponse,
)

_MANUAL_MSG = "AI 理解暂不可用，请使用手动开单、改单"


def parse_action(req: ParseActionRequest, gateway: ModelGateway) -> ParseActionResponse:
    """自然语言理解只走 LLM；不可用时直接失败，引导手动操作。"""
    action_id = str(uuid.uuid4())
    text = (req.text or "").strip()
    if not text:
        return ParseActionResponse(
            actionId=action_id,
            status="FAILED",
            message="指令不能为空",
            asrText=req.asrText,
            provider=gateway.provider_name,
            model=gateway.model_name,
        )

    if isinstance(gateway, StubModelGateway) or gateway.provider_name == "stub":
        return ParseActionResponse(
            actionId=action_id,
            status="FAILED",
            message=_MANUAL_MSG,
            asrText=req.asrText,
            provider=gateway.provider_name,
            model=gateway.model_name,
        )

    system = load_prompt("parse_action_system.txt")
    user = render_user_prompt(
        text=text,
        current_page=req.context.currentPage,
        current_order_id=req.context.currentOrderId,
        current_customer_id=req.context.currentCustomerId,
        current_customer_name=req.context.currentCustomerName,
        current_items=[it.model_dump() for it in req.context.currentItems],
        candidate_customers=[c.model_dump() for c in req.candidateCustomers],
        candidate_products=[p.model_dump() for p in req.candidateProducts],
    )
    try:
        raw = gateway.complete_json(system=system, user=user)
    except Exception:
        return ParseActionResponse(
            actionId=action_id,
            status="FAILED",
            message=_MANUAL_MSG,
            asrText=req.asrText,
            provider=gateway.provider_name,
            model=gateway.model_name,
        )

    if not isinstance(raw, dict) or raw.get("understood") is False:
        return ParseActionResponse(
            actionId=action_id,
            status="FAILED",
            message=_MANUAL_MSG,
            asrText=req.asrText,
            provider=gateway.provider_name,
            model=gateway.model_name,
        )

    return _to_response(raw, req, gateway, action_id=action_id)


def _to_response(
    raw: dict[str, Any],
    req: ParseActionRequest,
    gateway: ModelGateway,
    *,
    action_id: str,
) -> ParseActionResponse:
    status = raw.get("status") or "FAILED"
    if status not in {"READY", "NEED_USER_INPUT", "FAILED"}:
        status = "FAILED"

    allowed_customers = {c.customerId for c in req.candidateCustomers}
    allowed_items = {p.itemCode: p for p in req.candidateProducts}

    payload = raw.get("payload") if isinstance(raw.get("payload"), dict) else {}
    payload = dict(payload)

    customer = payload.get("customer")
    if isinstance(customer, dict):
        cid = customer.get("customerId")
        if cid and cid not in allowed_customers and cid != req.context.currentCustomerId:
            payload.pop("customer", None)
            status = "NEED_USER_INPUT"

    items = payload.get("items")
    if isinstance(items, list):
        cleaned_items = []
        for it in items:
            if not isinstance(it, dict):
                continue
            code = it.get("itemCode")
            if code not in allowed_items:
                continue
            product = allowed_items[code]
            uom = it.get("uom")
            if not uom and product.allowedUoms:
                uom = product.allowedUoms[0]
            if product.allowedUoms and uom not in product.allowedUoms:
                continue
            cleaned_items.append(
                {
                    "itemCode": product.itemCode,
                    "productId": product.productId,
                    "productName": product.productName,
                    "spec": product.spec,
                    "qty": it.get("qty"),
                    "uom": uom,
                }
            )
        payload["items"] = cleaned_items

    ops = payload.get("operations")
    if isinstance(ops, list):
        cleaned_ops = []
        for op in ops:
            if not isinstance(op, dict):
                continue
            code = op.get("itemCode")
            if code and code not in allowed_items and not any(
                (it.itemCode == code) for it in req.context.currentItems
            ):
                continue
            cleaned_ops.append(op)
        payload["operations"] = cleaned_ops

    ambiguities: list[Ambiguity] = []
    for amb in raw.get("ambiguities") or []:
        if not isinstance(amb, dict):
            continue
        cands = []
        for c in amb.get("candidates") or []:
            if not isinstance(c, dict):
                continue
            cands.append(
                AmbiguityCandidate(
                    itemCode=c.get("itemCode"),
                    customerId=c.get("customerId"),
                    name=c.get("name"),
                    spec=c.get("spec"),
                )
            )
        ambiguities.append(
            Ambiguity(
                field=str(amb.get("field") or "item"),
                expression=str(amb.get("expression") or ""),
                candidates=cands,
            )
        )

    action_type = raw.get("actionType")
    if action_type not in {"CREATE_ORDER", "UPDATE_CURRENT_ORDER", None}:
        action_type = None

    message = raw.get("message")
    if status == "FAILED" and not message:
        message = _MANUAL_MSG

    return ParseActionResponse(
        actionId=action_id,
        actionType=action_type,
        status=status,  # type: ignore[arg-type]
        targetPage=raw.get("targetPage") or ("ORDER_EDIT" if status != "FAILED" else None),
        ambiguities=ambiguities,
        payload=payload,
        asrText=req.asrText,
        provider=gateway.provider_name,
        model=gateway.model_name,
        message=message,
    )
