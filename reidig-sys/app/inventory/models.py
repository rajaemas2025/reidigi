from datetime import datetime

from sqlalchemy import ForeignKey, Index, Integer, String, Text, UniqueConstraint, Uuid, text
from sqlalchemy.orm import Mapped, mapped_column

from app.core import clock
from app.core.db import Base, BigId, UTCDateTime


class Location(Base):
    __tablename__ = "locations"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    code: Mapped[str] = mapped_column(String(16), unique=True)
    name: Mapped[str] = mapped_column(String(120))
    type: Mapped[str] = mapped_column(String(16))  # WAREHOUSE, BRANCH
    area: Mapped[str | None] = mapped_column(String(120))
    pickup_estimate: Mapped[str | None] = mapped_column(String(64))


class InboundBatch(Base):
    __tablename__ = "inbound_batches"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    sku_id: Mapped[int] = mapped_column(ForeignKey("skus.id"))
    location_id: Mapped[int] = mapped_column(ForeignKey("locations.id"))
    quantity: Mapped[int] = mapped_column(Integer)
    note: Mapped[str | None] = mapped_column(Text)
    received_by_admin_id: Mapped[int | None] = mapped_column(ForeignKey("admin_users.id"))
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class Unit(Base):
    __tablename__ = "units"
    __table_args__ = (
        UniqueConstraint("sku_id", "serial_number"),
        Index(
            "idx_units_available",
            "sku_id",
            "id",
            sqlite_where=text("status = 'AVAILABLE'"),
            postgresql_where=text("status = 'AVAILABLE'"),
        ),
    )

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    sku_id: Mapped[int] = mapped_column(ForeignKey("skus.id"))
    serial_number: Mapped[str] = mapped_column(String(64), index=True)
    location_id: Mapped[int] = mapped_column(ForeignKey("locations.id"))
    bin_location: Mapped[str | None] = mapped_column(String(32))
    status: Mapped[str] = mapped_column(String(16))
    inbound_batch_id: Mapped[int | None] = mapped_column(ForeignKey("inbound_batches.id"))
    version: Mapped[int] = mapped_column(Integer, default=0)

    __mapper_args__ = {"version_id_col": version}


class UnitEvent(Base):
    __tablename__ = "unit_events"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    unit_id: Mapped[int] = mapped_column(ForeignKey("units.id"), index=True)
    from_status: Mapped[str | None] = mapped_column(String(16))
    to_status: Mapped[str] = mapped_column(String(16))
    actor_type: Mapped[str] = mapped_column(String(16))  # USER, ADMIN, SYSTEM, PARTNER, GATEWAY
    actor_id: Mapped[str | None] = mapped_column(String(64))
    reason: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class TokenUnitHistory(Base):
    """Pertukaran keping di balik token, misalnya saat keping lama dikarantina."""

    __tablename__ = "token_unit_history"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    token_id = mapped_column(Uuid, ForeignKey("tokens.id"), index=True)
    from_unit_id: Mapped[int] = mapped_column(ForeignKey("units.id"))
    to_unit_id: Mapped[int] = mapped_column(ForeignKey("units.id"))
    reason: Mapped[str] = mapped_column(Text)
    actor_type: Mapped[str] = mapped_column(String(16))
    actor_id: Mapped[str | None] = mapped_column(String(64))
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class StockCount(Base):
    __tablename__ = "stock_counts"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    location_id: Mapped[int] = mapped_column(ForeignKey("locations.id"))
    # COUNTING, RECOUNT, AWAITING_APPROVAL, AWAITING_FINANCE, APPROVED
    status: Mapped[str] = mapped_column(String(20))
    # Disembunyikan dari petugas selama COUNTING (hitung buta).
    expected_unit_ids: Mapped[str] = mapped_column(Text, default="")
    created_by_admin_id: Mapped[int] = mapped_column(ForeignKey("admin_users.id"))
    approved_by_admin_id: Mapped[int | None] = mapped_column(ForeignKey("admin_users.id"))
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)

    @property
    def expected_ids(self) -> list[int]:
        return [int(x) for x in self.expected_unit_ids.split(",") if x]


class StockCountScan(Base):
    __tablename__ = "stock_count_scans"
    __table_args__ = (UniqueConstraint("count_id", "serial_number"),)

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    count_id: Mapped[int] = mapped_column(ForeignKey("stock_counts.id"), index=True)
    serial_number: Mapped[str] = mapped_column(String(64))
    scanned_by_admin_id: Mapped[int] = mapped_column(ForeignKey("admin_users.id"))
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, default=clock.now)


class StockCountVariance(Base):
    __tablename__ = "stock_count_variances"

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    count_id: Mapped[int] = mapped_column(ForeignKey("stock_counts.id"), index=True)
    type: Mapped[str] = mapped_column(String(16))  # MISSING, UNEXPECTED, WRONG_LOCATION
    serial_number: Mapped[str] = mapped_column(String(64))
    unit_id: Mapped[int | None] = mapped_column(ForeignKey("units.id"))
    recount_found: Mapped[bool | None] = mapped_column()
    recount_by_admin_id: Mapped[int | None] = mapped_column(ForeignKey("admin_users.id"))
