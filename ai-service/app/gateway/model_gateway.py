from __future__ import annotations

import json
from abc import ABC, abstractmethod
from typing import Any

import httpx

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


class OpenAiCompatibleGateway(ModelGateway):
    """OpenAI-compatible Chat Completions；差异收敛在 Gateway。"""

    def __init__(
        self,
        *,
        base_url: str,
        api_key: str,
        model: str,
        timeout_seconds: float = 30.0,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._model = model
        self._timeout = timeout_seconds

    def complete_json(self, *, system: str, user: str) -> dict[str, Any]:
        url = f"{self._base_url}/chat/completions"
        payload = {
            "model": self._model,
            "temperature": 0,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        }
        headers = {
            "Authorization": f"Bearer {self._api_key}",
            "Content-Type": "application/json",
        }
        with httpx.Client(timeout=self._timeout) as client:
            resp = client.post(url, headers=headers, json=payload)
            resp.raise_for_status()
            data = resp.json()
        content = (
            data.get("choices", [{}])[0]
            .get("message", {})
            .get("content", "{}")
        )
        if isinstance(content, dict):
            return content
        try:
            parsed = json.loads(content or "{}")
            return parsed if isinstance(parsed, dict) else {"raw": content}
        except json.JSONDecodeError:
            return {"raw": content, "understood": False}

    @property
    def provider_name(self) -> str:
        return "openai_compatible"

    @property
    def model_name(self) -> str:
        return self._model


def build_model_gateway() -> ModelGateway:
    provider = (settings.model_provider or "stub").lower()
    if provider == "stub" or not settings.openai_api_key:
        return StubModelGateway()
    return OpenAiCompatibleGateway(
        base_url=settings.openai_compatible_base_url,
        api_key=settings.openai_api_key,
        model=settings.openai_model,
    )
