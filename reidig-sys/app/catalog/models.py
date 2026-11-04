from sqlalchemy import BigInteger, CheckConstraint, ForeignKey, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core.db import Base, BigId


class Sku(Base):
    __tablename__ = "skus"
    __table_args__ = (CheckConstraint("weight_mg > 0", name="ck_skus_weight_positive"),)

    id: Mapped[int] = mapped_column(BigId, primary_key=True)
    code: Mapped[str] = mapped_column(String(32), unique=True)
    brand: Mapped[str] = mapped_column(String(64))
    product: Mapped[str] = mapped_column(String(64))
    weight_mg: Mapped[int] = mapped_column(BigInteger)
    is_active: Mapped[bool] = mapped_column(default=True)
    # Circuit breaker per SKU (pelanggaran invarian 1–3) atau henti manual Treasury.
    trading_halted: Mapped[bool] = mapped_column(default=False)
    halt_reason: Mapped[str | None] = mapped_column(Text)
    halted_by_admin_id: Mapped[int | None] = mapped_column(ForeignKey("admin_users.id"))
