from datetime import datetime

from sqlalchemy import JSON, BigInteger, ForeignKey, Integer, String, Text, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.core import clock
from app.core.db import Base, BigId, UTCDateTime


class FinancingAgreementVersion(Base):
    """Isi perjanjian per versi. Perubahan isi selalu menjadi versi baru."""

    __tablename__ = "financing_agreement_versions"

    version: Mapped[str] = mapped_column(String(32), primary_key=True)
    body: Mapped[str] = mapped_column(Text)
    content_hash: Mapped[str] = mapped_column(String(64))
    is_current: Mapped[bool] = mapped_column(default=False)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class FinancingAgreement(Base):
    """Bukti persetujuan. Append-only: dijaga trigger database."""

    __tablename__ = "financing_agreements"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    version: Mapped[str] = mapped_column(ForeignKey("financing_agreement_versions.version"))
    content_hash: Mapped[str] = mapped_column(String(64))
    sku_code: Mapped[str] = mapped_column(String(32))
    price: Mapped[int] = mapped_column(BigInteger)
    down_payment: Mapped[int] = mapped_column(BigInteger)
    tenor_months: Mapped[int] = mapped_column(Integer)
    monthly_before_fees: Mapped[int] = mapped_column(BigInteger)
    agreed_terms: Mapped[bool] = mapped_column()
    agreed_data_sharing: Mapped[bool] = mapped_column()
    device_id: Mapped[str | None] = mapped_column(String(128))
    ip_address: Mapped[str | None] = mapped_column(String(64))
    user_agent: Mapped[str | None] = mapped_column(Text)
    agreed_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class FinancingApplication(Base):
    __tablename__ = "financing_applications"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    sku_id: Mapped[int] = mapped_column(ForeignKey("skus.id"))
    unit_id: Mapped[int] = mapped_column(ForeignKey("units.id"))
    agreement_id: Mapped[int] = mapped_column(ForeignKey("financing_agreements.id"))
    price: Mapped[int] = mapped_column(BigInteger)
    down_payment: Mapped[int] = mapped_column(BigInteger)
    tenor_months: Mapped[int] = mapped_column(Integer)
    monthly_before_fees: Mapped[int] = mapped_column(BigInteger)
    # RESERVED (menunggu uang muka), PAID, CANCELLED, EXPIRED
    status: Mapped[str] = mapped_column(String(16), index=True)
    pay_before: Mapped[datetime] = mapped_column(UTCDateTime)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    closed_at: Mapped[datetime | None] = mapped_column(UTCDateTime)


class FinancingContract(Base):
    __tablename__ = "financing_contracts"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    contract_ref: Mapped[str] = mapped_column(String(32), unique=True)
    application_id: Mapped[int] = mapped_column(ForeignKey("financing_applications.id"), unique=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    partner: Mapped[str] = mapped_column(String(64))
    token_id = mapped_column(Uuid, ForeignKey("tokens.id"))
    price: Mapped[int] = mapped_column(BigInteger)
    down_payment: Mapped[int] = mapped_column(BigInteger)
    tenor_months: Mapped[int] = mapped_column(Integer)
    paid_months: Mapped[int] = mapped_column(Integer, default=0)
    # ON_TIME, OVERDUE, PAID_OFF, DEFAULTED
    status: Mapped[str] = mapped_column(String(16))
    agreement_version: Mapped[str] = mapped_column(String(32))
    agreed_at: Mapped[datetime] = mapped_column(UTCDateTime)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class FinancingEvent(Base):
    """Webhook dari mitra. event_id unik agar webhook ganda tidak diproses dua kali."""

    __tablename__ = "financing_events"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    event_id: Mapped[str] = mapped_column(String(64), unique=True)
    contract_id: Mapped[int] = mapped_column(ForeignKey("financing_contracts.id"), index=True)
    type: Mapped[str] = mapped_column(String(32))
    payload: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
