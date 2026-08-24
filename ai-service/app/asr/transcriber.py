from __future__ import annotations

from app.config import settings
from app.schemas.action import TranscribeResponse


def transcribe_audio(*, provider: str | None = None, filename: str | None = None) -> TranscribeResponse:
    """ASR：真实 Provider 后续接入。

    本地可用 AI_ASR_DEV_FIXED_TEXT 验证 Spring→AI 上传链路，不假装通用识别成功。
    """
    _ = filename
    resolved = (provider or settings.asr_provider or "stub").lower()
    fixed = (settings.asr_dev_fixed_text or "").strip()
    if fixed:
        return TranscribeResponse(text=fixed, segments=[], provider=f"{resolved}+dev_fixed")
    if resolved == "stub":
        return TranscribeResponse(text="", segments=[], provider="stub")
    return TranscribeResponse(text="", segments=[], provider=resolved)
