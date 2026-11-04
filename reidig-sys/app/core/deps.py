from fastapi import Depends, Header, Request
from sqlalchemy.orm import Session

from app.admin.models import AdminSession, AdminUser
from app.core import clock
from app.core.db import get_db
from app.core.errors import ApiError
from app.core.security import sha256_hex
from app.identity import service as identity
from app.identity.models import User

READ_ROLES = ("WAREHOUSE_STAFF", "WAREHOUSE_SUPERVISOR", "TREASURY", "FINANCE", "COMPLIANCE", "CS", "AUDITOR")


def bearer(authorization: str | None = Header(default=None)) -> str:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise ApiError("UNAUTHENTICATED", "Silakan masuk terlebih dahulu.")
    return authorization[7:].strip()


def current_user(token: str = Depends(bearer), db: Session = Depends(get_db)) -> User:
    user = identity.user_from_token(db, token)
    if user is None:
        raise ApiError("UNAUTHENTICATED", "Sesi berakhir. Silakan masuk lagi.")
    return user


def current_admin(token: str = Depends(bearer), db: Session = Depends(get_db)) -> AdminUser:
    from sqlalchemy import select

    session = db.scalar(select(AdminSession).where(AdminSession.token_hash == sha256_hex(token)))
    if session is None or session.revoked_at or session.expires_at <= clock.now():
        raise ApiError("UNAUTHENTICATED", "Sesi berakhir. Silakan masuk lagi.")
    admin = db.get(AdminUser, session.admin_id)
    if not admin or not admin.is_active:
        raise ApiError("UNAUTHENTICATED", "Akun tidak aktif.")
    return admin


def roles(*allowed: str):
    def check(admin: AdminUser = Depends(current_admin)) -> AdminUser:
        if admin.role not in allowed:
            raise ApiError("FORBIDDEN", "Peran Anda tidak berwenang melakukan aksi ini.")
        return admin

    return check


def client_info(request: Request) -> dict:
    return {
        "device_id": request.headers.get("x-device-id"),
        "ip": request.client.host if request.client else None,
        "user_agent": request.headers.get("user-agent"),
    }
