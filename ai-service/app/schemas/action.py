from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field


class ContextItem(BaseModel):
    itemCode: str | None = None
    productId: str | None = None
    productName: str | None = None
    spec: str | None = None
    qty: float | None = None
    uom: str | None = None
    rate: float | None = None


class AiContext(BaseModel):
    currentPage: str | None = None
    currentOrderId: str | None = None
    currentCustomerId: str | None = None
    currentCustomerName: str | None = None
    currentItems: list[ContextItem] = Field(default_factory=list)


class CandidateCustomer(BaseModel):
    customerId: str
    customerName: str
    aliases: list[str] = Field(default_factory=list)


class CandidateProduct(BaseModel):
    itemCode: str
    productId: str | None = None
    productName: str
    spec: str | None = None
    aliases: list[str] = Field(default_factory=list)
    allowedUoms: list[str] = Field(default_factory=list)


class ParseActionRequest(BaseModel):
    tenantId: str
    inputType: Literal["TEXT", "VOICE"] = "TEXT"
    text: str
    asrText: str | None = None
    context: AiContext = Field(default_factory=AiContext)
    candidateCustomers: list[CandidateCustomer] = Field(default_factory=list)
    candidateProducts: list[CandidateProduct] = Field(default_factory=list)


class AmbiguityCandidate(BaseModel):
    itemCode: str | None = None
    customerId: str | None = None
    name: str | None = None
    spec: str | None = None


class Ambiguity(BaseModel):
    field: str
    expression: str
    candidates: list[AmbiguityCandidate] = Field(default_factory=list)


class ParseActionResponse(BaseModel):
    actionId: str
    actionType: str | None = None
    status: Literal["READY", "NEED_USER_INPUT", "FAILED"] = "READY"
    targetPage: str | None = None
    resolvedEntities: dict[str, Any] = Field(default_factory=dict)
    ambiguities: list[Ambiguity] = Field(default_factory=list)
    payload: dict[str, Any] = Field(default_factory=dict)
    asrText: str | None = None
    provider: str | None = None
    model: str | None = None
    message: str | None = None


class TranscribeResponse(BaseModel):
    text: str
    segments: list[Any] = Field(default_factory=list)
    provider: str
