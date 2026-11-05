from datetime import datetime

from sqlalchemy import BigInteger, ForeignKey, Integer, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.core import clock
from app.core.db import Base, BigId, UTCDateTime


class Order(Base):
    __tablename__ = "orders"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    side: Mapped[str] = mapped_column(String(4))  # BUY, SELL
    # BUY: PENDING_PAYMENT, PAID, CANCELLED, EXPIRED, PAID_AFTER_EXPIRY. SELL: COMPLETED.
    status: Mapped[str] = mapped_column(String(20), index=True)
    payment_method: Mapped[str | None] = mapped_column(String(20))  # WALLET, QRIS, VIRTUAL_ACCOUNT
    quote_id: Mapped[str] = mapped_column(ForeignKey("quotes.id"), unique=True)
    sku_id: Mapped[int] = mapped_column(ForeignKey("skus.id"))
    quantity: Mapped[int] = mapped_column(Integer)
    unit_price: Mapped[int] = mapped_column(BigInteger)
    gross: Mapped[int] = mapped_column(BigInteger)
    tax: Mapped[int] = mapped_column(BigInteger, default=0)
    net: Mapped[int] = mapped_column(BigInteger)
    pay_before: Mapped[datetime | None] = mapped_column(UTCDateTime)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    completed_at: Mapped[datetime | None] = mapped_column(UTCDateTime)


class OrderItem(Base):
    __tablename__ = "order_items"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    order_id: Mapped[int] = mapped_column(ForeignKey("orders.id"), index=True)
    unit_id: Mapped[int] = mapped_column(ForeignKey("units.id"))
    token_id = mapped_column(Uuid, ForeignKey("tokens.id"), nullable=True)
