from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel

from auth import router as auth_router
from database import init_db
from incidents import router as incidents_router
from photos import PhotoUploadLimitMiddleware, router as photos_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    yield


app = FastAPI(
    title="SecureNote API",
    description="Локальный backend приложения SecureNote.",
    version="0.4.0",
    lifespan=lifespan,
)
app.include_router(auth_router)
app.include_router(incidents_router)
app.include_router(photos_router)
app.add_middleware(PhotoUploadLimitMiddleware)


class HealthResponse(BaseModel):
    status: str
    service: str


@app.get("/health", response_model=HealthResponse,
         tags=["Состояние сервера"], summary="Проверка доступности backend")
def health() -> HealthResponse:
    return HealthResponse(status="ok", service="SecureNote backend")
