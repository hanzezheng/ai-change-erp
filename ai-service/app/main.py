from fastapi import FastAPI

from app.api.internal import router as internal_router
from app.config import settings

app = FastAPI(title=settings.service_name, version="0.1.0")
app.include_router(internal_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": settings.service_name}
