from __future__ import annotations

from fastapi import APIRouter, File, Form, UploadFile

from app.asr.transcriber import transcribe_audio
from app.config import settings
from app.gateway.model_gateway import build_model_gateway
from app.intent.heuristic_parser import parse_action
from app.schemas.action import ParseActionRequest, ParseActionResponse, TranscribeResponse

router = APIRouter(prefix="/internal/ai", tags=["internal-ai"])


@router.post("/parse-action", response_model=ParseActionResponse)
def parse_action_endpoint(body: ParseActionRequest) -> ParseActionResponse:
    gateway = build_model_gateway()
    return parse_action(body, gateway)


@router.post("/speech/transcribe", response_model=TranscribeResponse)
async def transcribe_endpoint(
    file: UploadFile | None = File(default=None),
    objectKey: str | None = Form(default=None),
) -> TranscribeResponse:
    _ = file
    _ = objectKey
    return transcribe_audio(provider=settings.asr_provider)
