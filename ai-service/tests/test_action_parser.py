from app.gateway.model_gateway import ModelGateway, StubModelGateway
from app.intent.action_parser import parse_action
from app.prompts import load_prompt, render_user_prompt
from app.schemas.action import (
    AiContext,
    CandidateCustomer,
    CandidateProduct,
    ContextItem,
    ParseActionRequest,
)


class FakeGateway(ModelGateway):
    def __init__(self, payload: dict):
        self._payload = payload

    def complete_json(self, *, system: str, user: str) -> dict:
        assert "农批经营助手" in system or "JSON" in system
        return self._payload

    @property
    def provider_name(self) -> str:
        return "openai_compatible"

    @property
    def model_name(self) -> str:
        return "fake"


def _req(text: str = "老韩80果20箱，粉蕉30件") -> ParseActionRequest:
    return ParseActionRequest(
        tenantId="t1",
        text=text,
        candidateCustomers=[
            CandidateCustomer(customerId="韩兆亮", customerName="韩兆亮", aliases=["老韩"]),
        ],
        candidateProducts=[
            CandidateProduct(
                itemCode="APPLE-80",
                productId="APPLE",
                productName="苹果80果",
                spec="80果",
                allowedUoms=["箱", "斤"],
            ),
            CandidateProduct(
                itemCode="BANANA-FEN",
                productId="BANANA-FEN",
                productName="香蕉粉蕉",
                allowedUoms=["件", "箱"],
            ),
        ],
    )


def test_prompt_files_exist():
    assert "JSON" in load_prompt("parse_action_system.txt")
    user = render_user_prompt(
        text="测试",
        current_page="HOME",
        current_order_id=None,
        current_customer_id=None,
        current_customer_name=None,
        current_items=[],
        candidate_customers=[],
        candidate_products=[],
    )
    assert "测试" in user


def test_stub_gateway_fails_to_manual():
    resp = parse_action(_req(), StubModelGateway())
    assert resp.status == "FAILED"
    assert "手动" in (resp.message or "")


def test_llm_create_order_ready():
    gw = FakeGateway(
        {
            "actionType": "CREATE_ORDER",
            "status": "READY",
            "targetPage": "ORDER_EDIT",
            "payload": {
                "customer": {"customerId": "韩兆亮", "customerName": "韩兆亮"},
                "items": [
                    {"itemCode": "APPLE-80", "qty": 20, "uom": "箱"},
                    {"itemCode": "BANANA-FEN", "qty": 30, "uom": "件"},
                ],
            },
            "ambiguities": [],
        }
    )
    resp = parse_action(_req(), gw)
    assert resp.status == "READY"
    assert resp.actionType == "CREATE_ORDER"
    assert resp.payload["customer"]["customerId"] == "韩兆亮"
    assert [i["itemCode"] for i in resp.payload["items"]] == ["APPLE-80", "BANANA-FEN"]


def test_llm_update_current_order():
    req = ParseActionRequest(
        tenantId="t1",
        text="苹果改30箱",
        context=AiContext(
            currentPage="ORDER_EDIT",
            currentOrderId="SAL-ORD-1",
            currentCustomerId="韩兆亮",
            currentCustomerName="韩兆亮",
            currentItems=[
                ContextItem(itemCode="APPLE-80", productName="苹果80果", qty=20, uom="箱"),
            ],
        ),
        candidateProducts=[
            CandidateProduct(
                itemCode="APPLE-80",
                productId="APPLE",
                productName="苹果80果",
                allowedUoms=["箱"],
            ),
        ],
    )
    gw = FakeGateway(
        {
            "actionType": "UPDATE_CURRENT_ORDER",
            "status": "READY",
            "payload": {
                "orderId": "SAL-ORD-1",
                "operations": [
                    {"operation": "SET_QTY", "itemCode": "APPLE-80", "qty": 30, "uom": "箱"}
                ],
            },
        }
    )
    resp = parse_action(req, gw)
    assert resp.status == "READY"
    assert resp.actionType == "UPDATE_CURRENT_ORDER"
    assert resp.payload["operations"][0]["qty"] == 30


def test_llm_drops_unknown_item_code():
    gw = FakeGateway(
        {
            "actionType": "CREATE_ORDER",
            "status": "READY",
            "payload": {
                "customer": {"customerId": "韩兆亮", "customerName": "韩兆亮"},
                "items": [{"itemCode": "FAKE-99", "qty": 1, "uom": "箱"}],
            },
        }
    )
    resp = parse_action(_req(), gw)
    assert resp is not None
    assert resp.payload["items"] == []


def test_llm_exception_fails_to_manual():
    class BoomGateway(ModelGateway):
        def complete_json(self, *, system: str, user: str) -> dict:
            raise RuntimeError("network")

        @property
        def provider_name(self) -> str:
            return "openai_compatible"

        @property
        def model_name(self) -> str:
            return "x"

    resp = parse_action(_req(), BoomGateway())
    assert resp.status == "FAILED"
    assert "手动" in (resp.message or "")
