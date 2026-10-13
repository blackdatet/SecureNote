import logging
import re
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
from typing import Annotated
from uuid import uuid4

from fastapi import APIRouter, Depends, File, HTTPException, Response, UploadFile
from PIL import Image, ImageOps, UnidentifiedImageError
from pydantic import BaseModel

from auth import CurrentSession, get_current_session
from database import connect_db


router = APIRouter(prefix="/incidents", tags=["Фотографии"])
Session = Annotated[CurrentSession, Depends(get_current_session)]
PHOTO_DIR = Path(__file__).resolve().parent / "data" / "photos"
MAX_PHOTO_BYTES = 10 * 1024 * 1024
MAX_PHOTO_PIXELS = 20_000_000
MAX_UPLOAD_BYTES = MAX_PHOTO_BYTES + 64 * 1024
PHOTO_FIELDS = "incident_id, content_type, size_bytes, width, height, created_at, updated_at"
logger = logging.getLogger(__name__)


class PhotoResponse(BaseModel):
    incident_id: int
    content_type: str
    size_bytes: int
    width: int
    height: int
    created_at: datetime
    updated_at: datetime


class PhotoUploadLimitMiddleware:
    """Ограничение тела загрузки до разбора multipart, включая запросы без Content-Length."""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        is_upload = (
            scope["type"] == "http"
            and scope.get("method") == "PUT"
            and re.fullmatch(r"/incidents/[^/]+/photo/?", scope.get("path", ""))
        )
        if not is_upload:
            return await self.app(scope, receive, send)

        for name, value in scope.get("headers", []):
            if name.lower() == b"content-length":
                try:
                    length = int(value)
                except ValueError:
                    return await Response(status_code=400)(scope, receive, send)
                if length < 0:
                    return await Response(status_code=400)(scope, receive, send)
                if length > MAX_UPLOAD_BYTES:
                    response = Response(
                        content='{"detail":"Размер загрузки превышает 10 МБ"}',
                        status_code=413,
                        media_type="application/json",
                    )
                    return await response(scope, receive, send)

        received = 0

        async def limited_receive():
            nonlocal received
            message = await receive()
            if message["type"] == "http.request":
                received += len(message.get("body", b""))
                if received > MAX_UPLOAD_BYTES:
                    raise HTTPException(413, "Размер загрузки превышает 10 МБ")
            return message

        await self.app(scope, limited_receive, send)


def require_owner(connection, incident_id: int, user_id: int) -> None:
    row = connection.execute(
        "SELECT id FROM incidents WHERE id = ? AND owner_id = ?",
        (incident_id, user_id),
    ).fetchone()
    if row is None:
        raise HTTPException(404, "Инцидент не найден")


def photo_path(storage_name: str) -> Path:
    if re.fullmatch(r"[0-9a-f]{32}\.jpg", storage_name) is None:
        raise HTTPException(500, "Некорректные данные фотографии")
    return PHOTO_DIR / storage_name


def remove_photo_file(storage_name: str) -> None:
    try:
        photo_path(storage_name).unlink(missing_ok=True)
    except OSError:
        # Доступ к файлу уже отозван удалением его записи из базы.
        logger.warning("Не удалось удалить файл фотографии из локального хранилища")


def normalize_photo(content: bytes) -> tuple[bytes, int, int]:
    if not content:
        raise HTTPException(422, "Файл фотографии пуст")
    if len(content) > MAX_PHOTO_BYTES:
        raise HTTPException(413, "Фотография должна быть не больше 10 МБ")

    try:
        with Image.open(BytesIO(content)) as image:
            if image.format not in {"JPEG", "PNG"}:
                raise HTTPException(415, "Поддерживаются фотографии JPEG и PNG")
            if image.width * image.height > MAX_PHOTO_PIXELS:
                raise HTTPException(413, "Разрешение фотографии должно быть не больше 20 мегапикселей")
            if getattr(image, "n_frames", 1) != 1:
                raise HTTPException(415, "Анимированные изображения не поддерживаются")
            image.verify()

        # Повторное открытие после verify() и полное декодирование проверяют содержимое.
        with Image.open(BytesIO(content)) as image:
            image.load()
            oriented = ImageOps.exif_transpose(image)
            rgba = oriented.convert("RGBA")
            normalized = Image.new("RGB", rgba.size, (255, 255, 255))
            normalized.paste(rgba, mask=rgba.getchannel("A"))
            width, height = normalized.size
            output = BytesIO()
            # Новый JPEG не переносит EXIF, исходное имя и другие метаданные.
            normalized.save(output, format="JPEG", quality=88)
            encoded = output.getvalue()
    except Image.DecompressionBombError:
        raise HTTPException(413, "Разрешение фотографии слишком велико") from None
    except (UnidentifiedImageError, OSError, ValueError, SyntaxError):
        raise HTTPException(415, "Файл не является корректной фотографией JPEG или PNG") from None

    if len(encoded) > MAX_PHOTO_BYTES:
        raise HTTPException(413, "Сохранённая фотография превышает 10 МБ")
    return encoded, width, height


@router.get("/{incident_id}/photo/info", response_model=PhotoResponse | None,
            summary="Получение сведений о своей фотографии")
def photo_info(incident_id: int, session: Session) -> PhotoResponse | None:
    with connect_db() as connection:
        require_owner(connection, incident_id, session.user_id)
        row = connection.execute(
            f"SELECT {PHOTO_FIELDS} FROM incident_photos WHERE incident_id = ?",
            (incident_id,),
        ).fetchone()
    return PhotoResponse(**dict(row)) if row is not None else None


@router.put("/{incident_id}/photo", response_model=PhotoResponse,
            summary="Загрузка или замена своей фотографии")
def upload_photo(
    incident_id: int, session: Session, file: Annotated[UploadFile, File()],
) -> PhotoResponse:
    try:
        with connect_db() as connection:
            require_owner(connection, incident_id, session.user_id)
        content = file.file.read(MAX_PHOTO_BYTES + 1)
        encoded, width, height = normalize_photo(content)
    finally:
        file.file.close()

    storage_name = f"{uuid4().hex}.jpg"
    new_path = photo_path(storage_name)
    old_name = None
    file_created = False
    now = datetime.now(timezone.utc).isoformat()

    try:
        with connect_db() as connection:
            # Запись могла быть удалена во время обработки изображения.
            connection.execute("BEGIN IMMEDIATE")
            require_owner(connection, incident_id, session.user_id)
            old = connection.execute(
                "SELECT storage_name FROM incident_photos WHERE incident_id = ?",
                (incident_id,),
            ).fetchone()
            old_name = old["storage_name"] if old is not None else None
            PHOTO_DIR.mkdir(parents=True, exist_ok=True)
            with new_path.open("xb") as destination:
                file_created = True
                destination.write(encoded)
            connection.execute(
                """
                INSERT INTO incident_photos
                (incident_id, storage_name, content_type, size_bytes,
                 width, height, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(incident_id) DO UPDATE SET
                    storage_name = excluded.storage_name,
                    content_type = excluded.content_type,
                    size_bytes = excluded.size_bytes,
                    width = excluded.width,
                    height = excluded.height,
                    updated_at = excluded.updated_at
                """,
                (incident_id, storage_name, "image/jpeg", len(encoded),
                 width, height, now, now),
            )
            connection.execute(
                "UPDATE incidents SET updated_at = ? WHERE id = ? AND owner_id = ?",
                (now, incident_id, session.user_id),
            )
            row = connection.execute(
                f"SELECT {PHOTO_FIELDS} FROM incident_photos WHERE incident_id = ?",
                (incident_id,),
            ).fetchone()
    except Exception:
        if file_created:
            new_path.unlink(missing_ok=True)
        raise

    if old_name is not None:
        remove_photo_file(old_name)
    return PhotoResponse(**dict(row))


@router.get("/{incident_id}/photo", response_class=Response,
            responses={200: {"content": {"image/jpeg": {}}}},
            summary="Получение своей фотографии")
def get_photo(incident_id: int, session: Session) -> Response:
    with connect_db() as connection:
        # Чтение файла завершится до фиксации конкурирующей замены или удаления.
        connection.execute("BEGIN")
        require_owner(connection, incident_id, session.user_id)
        row = connection.execute(
            "SELECT storage_name FROM incident_photos WHERE incident_id = ?",
            (incident_id,),
        ).fetchone()
        if row is None:
            raise HTTPException(404, "Фотография не найдена")
        try:
            with photo_path(row["storage_name"]).open("rb") as source:
                content = source.read(MAX_PHOTO_BYTES + 1)
        except FileNotFoundError:
            raise HTTPException(404, "Фотография не найдена") from None
    if len(content) > MAX_PHOTO_BYTES:
        raise HTTPException(500, "Некорректный размер сохранённой фотографии")
    return Response(
        content=content,
        media_type="image/jpeg",
        headers={
            "Cache-Control": "no-store",
            "X-Content-Type-Options": "nosniff",
            "Content-Disposition": f'inline; filename="incident-{incident_id}.jpg"',
        },
    )


@router.delete("/{incident_id}/photo", status_code=204, response_class=Response,
               summary="Удаление своей фотографии")
def delete_photo(incident_id: int, session: Session) -> Response:
    with connect_db() as connection:
        connection.execute("BEGIN IMMEDIATE")
        require_owner(connection, incident_id, session.user_id)
        row = connection.execute(
            "SELECT storage_name FROM incident_photos WHERE incident_id = ?",
            (incident_id,),
        ).fetchone()
        if row is None:
            raise HTTPException(404, "Фотография не найдена")
        connection.execute("DELETE FROM incident_photos WHERE incident_id = ?", (incident_id,))
        connection.execute(
            "UPDATE incidents SET updated_at = ? WHERE id = ? AND owner_id = ?",
            (datetime.now(timezone.utc).isoformat(), incident_id, session.user_id),
        )
    remove_photo_file(row["storage_name"])
    return Response(status_code=204)
