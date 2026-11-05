import uuid
from datetime import timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.admin.audit import audit
from app.catalog.models import Sku
from app.core import clock
from app.core.config import get_settings
from app.core.errors import ApiError
from app.core.models import SystemFlag
from app.inventory.service import available_count
from app.pricing.models import PriceSourceRun, Quote, SkuPrice
from app.tokens.models import Token

TRADING_HALTED = "trading_halted"
WITHDRAWALS_FROZEN = "withdrawals_frozen"


def flag(db: Session, key: str) -> SystemFlag:
    item = db.get(SystemFlag, key)
    if item is None:
        item = SystemFlag(key=key, enabled=False)
        db.add(item)
        db.flush()
    return item


def ensure_trading_open(db: Session, sku: Sku) -> None:
    halted = db.get(SystemFlag, TRADING_HALTED)
    if halted and halted.enabled:
        raise ApiError("TRADING_HALTED", "Transaksi sedang dihentikan sementara. Coba lagi nanti.")
    if sku.trading_halted:
        raise ApiError("TRADING_HALTED", f"Transaksi {sku.code} sedang dihentikan sementara.")
    if not sku.is_active:
        raise ApiError("INVALID", "Produk ini tidak dijual.")


def latest_price(db: Session, sku_id: int) -> SkuPrice:
    price = db.scalar(select(SkuPrice).where(SkuPrice.sku_id == sku_id).order_by(SkuPrice.fetched_at.desc(), SkuPrice.id.desc()).limit(1))
    if price is None:
        raise ApiError("PRICE_STALE", "Harga belum tersedia.")
    max_age = get_settings().price_max_age_seconds
    if max_age and clock.now() - price.fetched_at > timedelta(seconds=max_age):
        raise ApiError("PRICE_STALE", "Harga sedang diperbarui. Coba lagi sebentar.")
    return price


def sku_by_code(db: Session, code: str) -> Sku:
    sku = db.scalar(select(Sku).where(Sku.code == code.strip().upper()))
    if sku is None:
        raise ApiError("NOT_FOUND", "Produk tidak ditemukan.")
    return sku


def create_quote(db: Session, user, sku_code: str, quantity: int, side: str) -> Quote:
    if quantity < 1:
        raise ApiError("INVALID", "Jumlah minimal 1 keping.")
    sku = sku_by_code(db, sku_code)
    ensure_trading_open(db, sku)
    price = latest_price(db, sku.id)
    if side == "BUY":
        if available_count(db, sku.id) < quantity:
            raise ApiError("INSUFFICIENT_STOCK", f"Stok {sku.code} tidak mencukupi.")
        unit_price = price.buy_price
    else:
        owned = db.scalars(select(Token.id).where(Token.user_id == user.id, Token.sku_id == sku.id, Token.status == "ACTIVE")).all()
        if len(owned) < quantity:
            raise ApiError("INVALID", "Jumlah token yang bisa dijual tidak mencukupi.")
        unit_price = price.buyback_price
    now = clock.now()
    quote = Quote(
        id=str(uuid.uuid4()),
        user_id=user.id,
        sku_id=sku.id,
        side=side,
        quantity=quantity,
        unit_price=unit_price,
        total=unit_price * quantity,
        buyback_value_now=price.buyback_price * quantity,
        price_id=price.id,
        created_at=now,
        expires_at=now + timedelta(seconds=get_settings().quote_ttl_seconds),
    )
    db.add(quote)
    db.flush()
    return quote


def consume_quote(db: Session, user, quote_id: str, side: str) -> Quote:
    quote = db.get(Quote, quote_id, with_for_update=True)
    if quote is None or quote.user_id != user.id or quote.side != side or quote.consumed_at:
        raise ApiError("QUOTE_EXPIRED", "Harga sudah berubah. Silakan ulangi.")
    if clock.now() > quote.expires_at:
        raise ApiError("QUOTE_EXPIRED", "Batas waktu harga habis. Silakan ulangi.")
    quote.consumed_at = clock.now()
    return quote


def record_prices(db: Session, actor, source: str, prices: list[tuple[str, int, int]], source_updated_at=None) -> PriceSourceRun:
    """prices: (kode SKU, harga beli, harga buyback). Dipakai job tarik harga dan input manual Treasury."""
    now = clock.now()
    run = PriceSourceRun(source=source, status="OK", started_at=now)
    db.add(run)
    db.flush()
    for code, buy, buyback in prices:
        if not 0 < buyback < buy:
            raise ApiError("INVALID", f"Harga {code}: buyback harus di bawah harga beli.")
        sku = sku_by_code(db, code)
        db.add(SkuPrice(sku_id=sku.id, buy_price=buy, buyback_price=buyback, source=source, source_updated_at=source_updated_at or now, fetched_at=now, run_id=run.id))
    run.finished_at = clock.now()
    run.detail = f"{len(prices)} SKU"
    if actor is not None:
        audit(db, actor, "PRICES_UPDATED", source, f"{len(prices)} SKU")
    return run


# ----- Henti perdagangan (Treasury, maker-checker) -----


def halt_trading(db: Session, actor, reason: str, sku_code: str | None = None) -> None:
    if not reason.strip():
        raise ApiError("INVALID", "Alasan wajib diisi.")
    if sku_code:
        sku = sku_by_code(db, sku_code)
        sku.trading_halted, sku.halt_reason, sku.halted_by_admin_id = True, reason.strip(), actor.id
        audit(db, actor, "SKU_TRADING_HALTED", sku.code, reason.strip())
        return
    item = flag(db, TRADING_HALTED)
    item.enabled, item.reason, item.set_by_admin_id, item.set_by_system, item.updated_at = True, reason.strip(), actor.id, False, clock.now()
    audit(db, actor, "TRADING_HALTED", "Transaksi", reason.strip())


def resume_trading(db: Session, actor, sku_code: str | None = None) -> None:
    if sku_code:
        sku = sku_by_code(db, sku_code)
        if sku.halted_by_admin_id == actor.id:
            raise ApiError("MAKER_CHECKER", "Transaksi harus dibuka kembali oleh orang yang berbeda.")
        sku.trading_halted, sku.halt_reason, sku.halted_by_admin_id = False, None, None
        audit(db, actor, "SKU_TRADING_RESUMED", sku.code)
        return
    item = flag(db, TRADING_HALTED)
    if item.set_by_admin_id == actor.id:
        raise ApiError("MAKER_CHECKER", "Transaksi harus dibuka kembali oleh orang yang berbeda.")
    item.enabled, item.reason, item.set_by_admin_id, item.set_by_system, item.updated_at = False, None, None, False, clock.now()
    audit(db, actor, "TRADING_RESUMED", "Transaksi")
