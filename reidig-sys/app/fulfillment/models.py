from datetime import datetime

from sqlalchemy import ForeignKey, String, Text, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.core import clock
from app.core.db import Base, BigId, UTCDateTime


class PrintOrder(Base):
    __tablename__ = "print_orders"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    method: Mapped[str] = mapped_column(String(8))  # SHIP (JNE), PICKUP (cabang)
    address: Mapped[str | None] = mapped_column(Text)
    branch_location_id: Mapped[int | None] = mapped_column(ForeignKey("locations.id"))
    # WAITING, PICKED, SHIPPED, IN_TRANSIT, READY_PICKUP, DONE, CANCELLED
    status: Mapped[str] = mapped_column(String(16), index=True)
    pickup_code: Mapped[str | None] = mapped_column(String(6))
    pickup_deadline: Mapped[datetime | None] = mapped_column(UTCDateTime)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class PrintOrderItem(Base):
    __tablename__ = "print_order_items"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    print_order_id: Mapped[int] = mapped_column(ForeignKey("print_orders.id"), index=True)
    token_id = mapped_column(Uuid, ForeignKey("tokens.id"))
    unit_id: Mapped[int] = mapped_column(ForeignKey("units.id"))
    picked: Mapped[bool] = mapped_column(default=False)
    arrived: Mapped[bool] = mapped_column(default=False)


class Shipment(Base):
    __tablename__ = "shipments"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    print_order_id: Mapped[int] = mapped_column(ForeignKey("print_orders.id"), index=True)
    kind: Mapped[str] = mapped_column(String(16))  # TO_CUSTOMER, TO_BRANCH
    carrier: Mapped[str] = mapped_column(String(32))
    tracking_number: Mapped[str] = mapped_column(String(64))
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)
