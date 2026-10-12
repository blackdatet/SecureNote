import hashlib
import secrets
import sqlite3
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, HTTPException, Response
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, EmailStr, Field, SecretStr, field_validator
from pwdlib import PasswordHash

from database import connect_db


router = APIRouter(prefix="/auth", tags=["Авторизация"])
bearer = HTTPBearer(auto_error=False)
password_hasher = PasswordHash.recommended()
DUMMY_HASH = password_hasher.hash(secrets.token_urlsafe(32))
SESSION_TTL = 24 * 60 * 60


class RegisterRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    email: EmailStr = Field(max_length=254)
    password: SecretStr = Field(min_length=8, max_length=128)

    @field_validator("password")
    @classmethod
    def validate_password(cls, value: SecretStr) -> SecretStr:
        if not value.get_secret_value().strip():
            raise ValueError("Пароль не может состоять только из пробелов")
        return value


class LoginRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    email: EmailStr = Field(max_length=254)
    password: SecretStr = Field(min_length=1, max_length=128)


class UserResponse(BaseModel):
    id: int
    email: str
    created_at: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: Literal["bearer"] = "bearer"
    expires_in: int


@dataclass(frozen=True)
class CurrentSession:
    user_id: int
    email: str
    created_at: str
    token_hash: str


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def unauthorized() -> HTTPException:
    return HTTPException(
        status_code=401,
        detail="Требуется вход: сессия отсутствует или недействительна",
        headers={"WWW-Authenticate": "Bearer"},
    )


def get_current_session(
    credentials: Annotated[
        HTTPAuthorizationCredentials | None, Depends(bearer)
    ],
) -> CurrentSession:
    if credentials is None:
        raise unauthorized()

    token_hash = hash_token(credentials.credentials)
    with connect_db() as connection:
        row = connection.execute(
            """
            SELECT users.id, users.email, users.created_at, sessions.token_hash
            FROM sessions
            JOIN users ON users.id = sessions.user_id
            WHERE sessions.token_hash = ? AND sessions.expires_at > ?
            """,
            (token_hash, int(time.time())),
        ).fetchone()

    if row is None:
        raise unauthorized()

    return CurrentSession(
        user_id=row["id"],
        email=row["email"],
        created_at=row["created_at"],
        token_hash=row["token_hash"],
    )


@router.post("/register", response_model=UserResponse, status_code=201,
             summary="Регистрация пользователя")
def register(data: RegisterRequest) -> UserResponse:
    email = str(data.email).lower()
    password_hash = password_hasher.hash(data.password.get_secret_value())
    created_at = datetime.now(timezone.utc).isoformat()

    try:
        with connect_db() as connection:
            cursor = connection.execute(
                "INSERT INTO users (email, password_hash, created_at) VALUES (?, ?, ?)",
                (email, password_hash, created_at),
            )
            user_id = cursor.lastrowid
    except sqlite3.IntegrityError:
        raise HTTPException(
            status_code=409, detail="Пользователь с таким email уже существует"
        ) from None

    return UserResponse(id=user_id, email=email, created_at=created_at)


@router.post("/login", response_model=TokenResponse,
             summary="Вход и создание сессии")
def login(data: LoginRequest, response: Response) -> TokenResponse:
    email = str(data.email).lower()
    with connect_db() as connection:
        user = connection.execute(
            "SELECT id, password_hash FROM users WHERE email = ?", (email,)
        ).fetchone()

    stored_hash = user["password_hash"] if user is not None else DUMMY_HASH
    valid_password = password_hasher.verify(
        data.password.get_secret_value(), stored_hash
    )

    if user is None or not valid_password:
        raise HTTPException(
            status_code=401,
            detail="Неверный email или пароль",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = secrets.token_urlsafe(32)
    now = int(time.time())
    with connect_db() as connection:
        connection.execute("DELETE FROM sessions WHERE expires_at <= ?", (now,))
        connection.execute(
            "INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
            (hash_token(token), user["id"], now + SESSION_TTL),
        )

    response.headers["Cache-Control"] = "no-store"
    return TokenResponse(access_token=token, expires_in=SESSION_TTL)


@router.get("/me", response_model=UserResponse,
            summary="Получение своего профиля")
def me(
    session: Annotated[CurrentSession, Depends(get_current_session)],
) -> UserResponse:
    return UserResponse(
        id=session.user_id, email=session.email, created_at=session.created_at
    )


@router.post("/logout", status_code=204, response_class=Response,
             summary="Выход и отзыв текущей сессии")
def logout(
    session: Annotated[CurrentSession, Depends(get_current_session)],
) -> Response:
    with connect_db() as connection:
        connection.execute(
            "DELETE FROM sessions WHERE token_hash = ?", (session.token_hash,)
        )
    return Response(status_code=204)
