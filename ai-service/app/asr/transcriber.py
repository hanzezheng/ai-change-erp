from __future__ import annotations

from app.schemas.action import TranscribeResponse


def transcribe_audio(*, provider: str = "stub") -> TranscribeResponse:
    """ASR Stub：真实 Provider 后续接入；当前不假装识别成功。"""
    if provider.lower() == "stub":
        return TranscribeResponse(
            text="",
            segments=[],
            provider="stub",
        )
    return TranscribeResponse(text="", segments=[], provider=provider)
