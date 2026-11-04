from datetime import datetime

from sqlalchemy import BigInteger, CheckConstraint, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core import clock
from app.core.db import Base, BigId, UTCDateTime


class SkuPrice(Base):
    """Harga per SKU, karena harga per gram tiap pecahan berbeda. Baris baru setiap tarikan; tidak diubah."""

    __tablename__ = "sku_prices"
    __table_args__ = (
        CheckConstraint("buy_price > 0", name="ck_price_buy_positive"),
        CheckConstraint("buyback_price > 0", name="ck_price_buyback_positive"),
        CheckConstraint("buyback_price < buy_price", name="ck_price_spread"),
    )

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    sku_id: Mapped[int] = mapped_column(ForeignKey("skus.id"), index=True)
    buy_price: Mapped[int] = mapped_column(BigInteger)
    buyback_price: Mapped[int] = mapped_column(BigInteger)
    source: Mapped[str] = mapped_column(String(64))
    source_updated_at: Mapped[datetime] = mapped_column(UTCDateTime)
    fetched_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    run_id: Mapped[int | None] = mapped_column(ForeignKey("price_source_runs.id"))


class PriceSourceRun(Base):
    __tablename__ = "price_source_runs"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    source: Mapped[str] = mapped_column(String(64))
    status: Mapped[str] = mapped_column(String(16))  # OK, FAILED
    detail: Mapped[str | None] = mapped_column(Text)
    started_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    finished_at: Mapped[datetime | None] = mapped_column(UTCDateTime)


class Quote(Base):
    __tablename__ = "quotes"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    sku_id: Mapped[int] = mapped_column(ForeignKey("skus.id"))
    side: Mapped[str] = mapped_column(String(4))  # BUY, SELL
    quantity: Mapped[int] = mapped_column(Integer)
    unit_price: Mapped[int] = mapped_column(BigInteger)
    total: Mapped[int] = mapped_column(BigInteger)
    buyback_value_now: Mapped[int] = mapped_column(BigInteger)
    price_id: Mapped[int] = mapped_column(ForeignKey("sku_prices.id"))
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    expires_at: Mapped[datetime] = mapped_column(UTCDateTime)
    consumed_at: Mapped[datetime | None] = mapped_column(UTCDateTime)
