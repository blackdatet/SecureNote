from datetime import datetime, timezone
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, HTTPException, Response
from pydantic import BaseModel, ConfigDict, Field

from auth import CurrentSession, get_current_session
from database import connect_db


router = APIRouter(prefix="/incidents", tags=["Инциденты"])
Session = Annotated[CurrentSession, Depends(get_current_session)]
Category = Literal["Фишинг", "Подозрительный вход", "Утечка данных", "Другое"]
Severity = Literal["Низкий", "Средний", "Высокий"]
IncidentStatus = Literal["Новый", "В работе", "Закрыт"]


class IncidentCreate(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)
    title: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=2000)
    category: Category
    severity: Severity


class IncidentUpdate(IncidentCreate):
    status: IncidentStatus


class IncidentResponse(IncidentUpdate):
    id: int
    created_at: datetime
    updated_at: datetime


FIELDS = "id, title, description, category, severity, status, created_at, updated_at"


def not_found() -> HTTPException:
    return HTTPException(status_code=404, detail="Инцидент не найден")


@router.get("", response_model=list[IncidentResponse],
            summary="Получение своего журнала")
def list_incidents(session: Session) -> list[IncidentResponse]:
    with connect_db() as connection:
        rows = connection.execute(
            f"SELECT {FIELDS} FROM incidents WHERE owner_id = ? "
            "ORDER BY created_at DESC, id DESC",
            (session.user_id,),
        ).fetchall()
    return [IncidentResponse(**dict(row)) for row in rows]


@router.post("", response_model=IncidentResponse, status_code=201,
             summary="Создание инцидента")
def create_incident(data: IncidentCreate, session: Session) -> IncidentResponse:
    now = datetime.now(timezone.utc).isoformat()
    with connect_db() as connection:
        cursor = connection.execute(
            """
            INSERT INTO incidents
            (owner_id, title, description, category, severity, status,
             created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (session.user_id, data.title, data.description, data.category,
             data.severity, "Новый", now, now),
        )
        incident_id = cursor.lastrowid

    return IncidentResponse(
        id=incident_id, **data.model_dump(), status="Новый",
        created_at=now, updated_at=now,
    )


@router.get("/{incident_id}", response_model=IncidentResponse,
            summary="Получение своей записи")
def get_incident(incident_id: int, session: Session) -> IncidentResponse:
    with connect_db() as connection:
        row = connection.execute(
            f"SELECT {FIELDS} FROM incidents WHERE id = ? AND owner_id = ?",
            (incident_id, session.user_id),
        ).fetchone()
    if row is None:
        raise not_found()
    return IncidentResponse(**dict(row))


@router.put("/{incident_id}", response_model=IncidentResponse,
            summary="Редактирование своей записи")
def update_incident(
    incident_id: int, data: IncidentUpdate, session: Session,
) -> IncidentResponse:
    now = datetime.now(timezone.utc).isoformat()
    with connect_db() as connection:
        cursor = connection.execute(
            """
            UPDATE incidents
            SET title = ?, description = ?, category = ?, severity = ?,
                status = ?, updated_at = ?
            WHERE id = ? AND owner_id = ?
            """,
            (data.title, data.description, data.category, data.severity,
             data.status, now, incident_id, session.user_id),
        )
        if cursor.rowcount == 0:
            raise not_found()
        row = connection.execute(
            f"SELECT {FIELDS} FROM incidents WHERE id = ? AND owner_id = ?",
            (incident_id, session.user_id),
        ).fetchone()
    return IncidentResponse(**dict(row))


@router.delete("/{incident_id}", status_code=204, response_class=Response,
               summary="Удаление своей записи")
def delete_incident(incident_id: int, session: Session) -> Response:
    with connect_db() as connection:
        cursor = connection.execute(
            "DELETE FROM incidents WHERE id = ? AND owner_id = ?",
            (incident_id, session.user_id),
        )
        if cursor.rowcount == 0:
            raise not_found()
    return Response(status_code=204)
