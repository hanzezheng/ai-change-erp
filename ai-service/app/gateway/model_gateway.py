from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any

from app.config import settings


class ModelGateway(ABC):
    """统一模型出口；业务代码不得直接绑死某一 Provider。"""

    @abstractmethod
    def complete_json(self, *, system: str, user: str) -> dict[str, Any]:
        raise NotImplementedError

    @property
    @abstractmethod
    def provider_name(self) -> str:
        raise NotImplementedError

    @property
    @abstractmethod
    def model_name(self) -> str:
        raise NotImplementedError


class StubModelGateway(ModelGateway):
    """无 LLM Key 时的占位实现：不调用外网，返回空理解结果。"""

    def complete_json(self, *, system: str, user: str) -> dict[str, Any]:
        return {"understood": False, "reason": "stub_provider"}

    @property
    def provider_name(self) -> str:
        return "stub"

    @property
    def model_name(self) -> str:
        return "stub-v0"


def build_model_gateway() -> ModelGateway:
    if settings.model_provider.lower() == "stub" or not settings.openai_api_key:
        return StubModelGateway()
    # 正式 OpenAI-compatible 客户端后续接入；当前无 Key 时强制 stub，避免误绑。
    return StubModelGateway()
