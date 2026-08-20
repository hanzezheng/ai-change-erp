from app.gateway.model_gateway import StubModelGateway
from app.intent.heuristic_parser import parse_action
from app.schemas.action import (
    AiContext,
    CandidateCustomer,
    CandidateProduct,
    ContextItem,
    ParseActionRequest,
)


def test_create_order_golden_path():
    req = ParseActionRequest(
        tenantId="t1",
        text="老韩80果20箱，粉蕉30件",
        candidateCustomers=[
            CandidateCustomer(
                customerId="C-HAN",
                customerName="韩兆亮",
                aliases=["老韩", "韩老板"],
            )
        ],
        candidateProducts=[
            CandidateProduct(
                itemCode="APPLE-80",
                productId="APPLE",
                productName="苹果80果",
                spec="80果",
                aliases=["八零"],
                allowedUoms=["箱", "斤"],
            ),
            CandidateProduct(
                itemCode="BANANA-FEN",
                productId="BANANA-FEN",
                productName="香蕉粉蕉",
                aliases=["粉蕉"],
                allowedUoms=["件", "箱"],
            ),
        ],
    )
    resp = parse_action(req, StubModelGateway())
    assert resp.status == "READY"
    assert resp.actionType == "CREATE_ORDER"
    assert resp.payload["customer"]["customerId"] == "C-HAN"
    items = resp.payload["items"]
    assert len(items) == 2
    assert items[0]["itemCode"] == "APPLE-80"
    assert items[0]["qty"] == 20
    assert items[0]["uom"] == "箱"
    assert items[1]["itemCode"] == "BANANA-FEN"
    assert items[1]["qty"] == 30


def test_update_current_order_set_qty():
    req = ParseActionRequest(
        tenantId="t1",
        text="苹果改30箱",
        context=AiContext(
            currentPage="ORDER_EDIT",
            currentOrderId="SAL-ORD-1",
            currentCustomerId="C-HAN",
            currentCustomerName="韩兆亮",
            currentItems=[
                ContextItem(itemCode="APPLE-80", productName="苹果80果", qty=20, uom="箱"),
                ContextItem(itemCode="BANANA-FEN", productName="香蕉粉蕉", qty=30, uom="件"),
            ],
        ),
    )
    resp = parse_action(req, StubModelGateway())
    assert resp.status == "READY"
    assert resp.actionType == "UPDATE_CURRENT_ORDER"
    ops = resp.payload["operations"]
    assert ops[0]["operation"] == "SET_QTY"
    assert ops[0]["itemCode"] == "APPLE-80"
    assert ops[0]["qty"] == 30


def test_customer_ambiguity():
    req = ParseActionRequest(
        tenantId="t1",
        text="老韩苹果80果20箱",
        candidateCustomers=[
            CandidateCustomer(customerId="C1", customerName="韩兆亮", aliases=["老韩"]),
            CandidateCustomer(customerId="C2", customerName="韩兆良", aliases=["老韩"]),
        ],
        candidateProducts=[
            CandidateProduct(
                itemCode="APPLE-80",
                productId="APPLE",
                productName="苹果80果",
                spec="80果",
                allowedUoms=["箱"],
            )
        ],
    )
    resp = parse_action(req, StubModelGateway())
    assert resp.status == "NEED_USER_INPUT"
    assert resp.ambiguities[0].field == "customer"
    assert len(resp.ambiguities[0].candidates) == 2
