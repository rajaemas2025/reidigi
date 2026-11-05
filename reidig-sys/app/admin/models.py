from datetime import datetime

from sqlalchemy import ForeignKey, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core import clock
from app.core.db import Base, BigId, UTCDateTime

ROLES = {
    "WAREHOUSE_STAFF": "Petugas gudang dan cabang",
    "WAREHOUSE_SUPERVISOR": "Supervisor gudang",
    "TREASURY": "Treasury: harga dan henti perdagangan",
    "FINANCE": "Finance: penarikan dana dan selisih besar",
    "COMPLIANCE": "Compliance: KYC",
    "CS": "Layanan pelanggan",
    "AUDITOR": "Auditor: hanya baca",
}


class Role(Base):
    __tablename__ = "roles"

    code: Mapped[str] = mapped_column(String(32), primary_key=True)
    description: Mapped[str] = mapped_column(Text)


class AdminUser(Base):
    __tablename__ = "admin_users"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    email: Mapped[str] = mapped_column(String(254), unique=True)
    name: Mapped[str] = mapped_column(String(120))
    role: Mapped[str] = mapped_column(ForeignKey("roles.code"))
    password_hash: Mapped[str] = mapped_column(String(200))
    is_active: Mapped[bool] = mapped_column(default=True)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class AdminSession(Base):
    __tablename__ = "admin_sessions"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    admin_id: Mapped[int] = mapped_column(ForeignKey("admin_users.id"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    expires_at: Mapped[datetime] = mapped_column(UTCDateTime)
    revoked_at: Mapped[datetime | None] = mapped_column(UTCDateTime)


class AuditLog(Base):
    __tablename__ = "audit_logs"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    actor_type: Mapped[str] = mapped_column(String(16))  # ADMIN, USER, SYSTEM
    actor_id: Mapped[str | None] = mapped_column(String(64))
    actor_name: Mapped[str] = mapped_column(String(120))
    action: Mapped[str] = mapped_column(String(48), index=True)
    entity: Mapped[str] = mapped_column(String(120))
    detail: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now, index=True)
