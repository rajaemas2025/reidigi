from datetime import date, datetime

from sqlalchemy import Date, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core import clock
from app.core.db import Base, BigId, UTCDateTime


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    name: Mapped[str] = mapped_column(String(120))
    email: Mapped[str] = mapped_column(String(254), unique=True)
    phone: Mapped[str | None] = mapped_column(String(20))
    date_of_birth: Mapped[date | None] = mapped_column(Date)
    password_hash: Mapped[str] = mapped_column(String(200))
    pin_hash: Mapped[str | None] = mapped_column(String(200))
    pin_failed_attempts: Mapped[int] = mapped_column(Integer, default=0)
    pin_locked: Mapped[bool] = mapped_column(default=False)
    # NONE, PENDING, APPROVED, REJECTED
    kyc_status: Mapped[str] = mapped_column(String(16), default="NONE")
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class UserSession(Base):
    __tablename__ = "user_sessions"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    device_id: Mapped[str | None] = mapped_column(String(128))
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    expires_at: Mapped[datetime] = mapped_column(UTCDateTime)
    revoked_at: Mapped[datetime | None] = mapped_column(UTCDateTime)


class EmailCode(Base):
    """Kode 6 angka yang dikirim ke email, untuk daftar akun dan atur ulang kata sandi.

    Setelah kode benar, pengguna mendapat tiket sekali pakai untuk langkah berikutnya.
    Kode dan tiket hanya disimpan dalam bentuk hash.
    """

    __tablename__ = "email_codes"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    email: Mapped[str] = mapped_column(String(254), index=True)
    purpose: Mapped[str] = mapped_column(String(16))  # REGISTER, RESET_PASSWORD
    code_hash: Mapped[str] = mapped_column(String(200))
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    expires_at: Mapped[datetime] = mapped_column(UTCDateTime)
    verified_at: Mapped[datetime | None] = mapped_column(UTCDateTime)
    ticket_hash: Mapped[str | None] = mapped_column(String(64), unique=True)
    consumed_at: Mapped[datetime | None] = mapped_column(UTCDateTime)


class Device(Base):
    __tablename__ = "devices"
    __table_args__ = (UniqueConstraint("user_id", "device_id"),)

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    device_id: Mapped[str] = mapped_column(String(128))
    user_agent: Mapped[str | None] = mapped_column(Text)
    first_seen_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    last_seen_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class KycRecord(Base):
    __tablename__ = "kyc_records"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    full_name: Mapped[str] = mapped_column(String(120))
    # NIK tidak disimpan utuh: hash berpepper untuk pencocokan, 4 digit akhir untuk tampilan.
    nik_hash: Mapped[str] = mapped_column(String(64), index=True)
    nik_last4: Mapped[str] = mapped_column(String(4))
    date_of_birth: Mapped[date] = mapped_column(Date)
    status: Mapped[str] = mapped_column(String(16))  # PENDING, APPROVED, REJECTED
    reason: Mapped[str | None] = mapped_column(Text)
    vendor_ref: Mapped[str | None] = mapped_column(String(64))
    decided_by_admin_id: Mapped[int | None] = mapped_column(ForeignKey("admin_users.id"))
    decision_note: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    decided_at: Mapped[datetime | None] = mapped_column(UTCDateTime)


class BankAccount(Base):
    __tablename__ = "bank_accounts"
    __table_args__ = (UniqueConstraint("user_id", "bank_code", "account_number"),)

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    bank_code: Mapped[str] = mapped_column(String(16))
    account_number: Mapped[str] = mapped_column(String(32))
    holder_name: Mapped[str] = mapped_column(String(120))
    verified: Mapped[bool] = mapped_column(default=False)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
