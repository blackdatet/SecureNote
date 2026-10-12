from fastapi import FastAPI
from pydantic import BaseModel


app = FastAPI(
    title="SecureNote API",
    description="Локальный backend приложения SecureNote.",
    version="0.1.0",
)


class HealthResponse(BaseModel):
    status: str
    service: str


@app.get(
    "/health",
    response_model=HealthResponse,
    tags=["Состояние сервера"],
    summary="Проверка доступности backend",
)
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        service="SecureNote backend",
    )
