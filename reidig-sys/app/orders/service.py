"""Beli dan jual emas (spesifikasi 6.1 dan 6.2)."""

from datetime import timedelta

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.catalog.models import Sku
from app.core import clock
from app.core.config import get_settings
from app.core.errors import ApiError
from app.core.models import emit
from app.identity.service import require_verified, verify_pin
from app.inventory import service as inventory
from app.inventory.models import Unit
from app.ledger import service as ledger
from app.orders.models import Order, OrderItem
from app.payments import service as payments
from app.payments.models import Payment
from app.pricing import service as pricing
from app.tokens import service as tokens
from app.tokens.models import Token
from app.wallet import service as wallet

METHODS = ("WALLET", "QRIS", "VIRTUAL_ACCOUNT")


def _check_daily_limit(db: Session, user_id: int, amount: int) -> None:
    limit = get_settings().daily_buy_limit
    if not limit:
        return
    since = clock.now() - timedelta(days=1)
    spent = db.scalar(
        select(func.coalesce(func.sum(Order.gross), 0)).where(
            Order.user_id == user_id, Order.side == "BUY", Order.status.in_(("PAID", "PENDING_PAYMENT")), Order.created_at >= since
        )
    )
    if spent + amount > limit:
        raise ApiError("LIMIT_EXCEEDED", "Melebihi batas pembelian harian.")


def _complete_buy(db: Session, order: Order, units: list[Unit], actor_type: str, actor_id) -> list[Token]:
    sku = db.get(Sku, order.sku_id)
    minted = []
    for unit in units:
        inventory.set_status(db, unit, "ALLOCATED", actor_type, actor_id, f"Order #{order.id} lunas")
        token = tokens.mint(
            db, user_id=order.user_id, unit=unit, sku=sku, status="ACTIVE", price=order.unit_price,
            origin_type="ORDER", origin_id=order.id, actor_type=actor_type, actor_id=actor_id,
        )
        item = db.scalar(select(OrderItem).where(OrderItem.order_id == order.id, OrderItem.unit_id == unit.id))
        item.token_id = token.id
        minted.append(token)
    order.status = "PAID"
    order.completed_at = clock.now()
    emit(db, "order.paid", order_id=order.id, user_id=order.user_id, tokens=[t.token_code for t in minted])
    return minted


def buy(db: Session, user, quote_id: str, method: str) -> tuple[Order, list[Token], Payment | None]:
    if method not in METHODS:
        raise ApiError("INVALID", "Metode pembayaran tidak dikenal.")
    require_verified(user)
    quote = pricing.consume_quote(db, user, quote_id, "BUY")
    sku = db.get(Sku, quote.sku_id)
    pricing.ensure_trading_open(db, sku)
    _check_daily_limit(db, user.id, quote.total)

    order = Order(
        user_id=user.id, side="BUY", status="PENDING_PAYMENT", payment_method=method, quote_id=quote.id,
        sku_id=sku.id, quantity=quote.quantity, unit_price=quote.unit_price, gross=quote.total, tax=0, net=quote.total,
    )
    db.add(order)
    db.flush()
    # Kunci keping lebih dulu, baru saldo.
    units = inventory.reserve_units(db, sku.id, quote.quantity, "USER", user.id, f"Order #{order.id}")
    db.add_all(OrderItem(order_id=order.id, unit_id=u.id) for u in units)
    db.flush()

    if method == "WALLET":
        wallet.debit(db, user.id, order.gross, "PURCHASE_DEBIT", "ORDER", order.id)
        ledger.post(
            db,
            [
                (ledger.WALLET_LIABILITY, order.gross, 0),
                (ledger.GOLD_SALES, 0, order.gross),
                (ledger.OPERATING, order.gross, 0),
                (ledger.CUSTOMER_FUNDS, 0, order.gross),
            ],
            "ORDER", order.id, "Beli emas dengan saldo",
        )
        return order, _complete_buy(db, order, units, "USER", user.id), None

    order.pay_before = clock.now() + timedelta(seconds=get_settings().payment_window_seconds)
    payment = payments.create_payment(
        db, user_id=user.id, purpose="ORDER", reference_id=order.id, method=method, amount=order.gross, expires_at=order.pay_before
    )
    return order, [], payment


def _order_units(db: Session, order_id: int) -> list[Unit]:
    unit_ids = db.scalars(select(OrderItem.unit_id).where(OrderItem.order_id == order_id)).all()
    return list(inventory.lock_units(db, list(unit_ids)).values())


def _release_order(db: Session, order: Order, status: str, actor_type: str, actor_id, reason: str) -> None:
    for unit in _order_units(db, order.id):
        if unit.status == "RESERVED":
            inventory.set_status(db, unit, "AVAILABLE", actor_type, actor_id, reason)
    order.status = status
    order.completed_at = clock.now()
    payments.close_pending(db, "ORDER", order.id, "EXPIRED" if status == "EXPIRED" else "CANCELLED")


def on_payment_paid(db: Session, payment: Payment) -> None:
    order = db.get(Order, payment.reference_id, with_for_update=True)
    if order.status == "PENDING_PAYMENT" and clock.now() > order.pay_before:
        _release_order(db, order, "EXPIRED", "SYSTEM", None, "Batas bayar habis")
    if order.status == "PENDING_PAYMENT":
        payment.status = "PAID"
        _complete_buy(db, order, _order_units(db, order.id), "GATEWAY", payment.provider_ref)
        ledger.post(db, [(ledger.PG_CLEARING, order.gross, 0), (ledger.GOLD_SALES, 0, order.gross)], "ORDER", order.id, f"Beli emas lewat {payment.method}")
        return
    # Dana masuk setelah order kedaluwarsa atau dibatalkan: jangan terbitkan token dengan harga lama.
    refund_late_payment(db, payment)
    if order.status in ("EXPIRED", "CANCELLED"):
        order.status = "PAID_AFTER_EXPIRY"


def refund_late_payment(db: Session, payment: Payment) -> None:
    payment.status = "REFUNDED"
    wallet.credit(db, payment.user_id, payment.amount, "REFUND", "PAYMENT", payment.id)
    ledger.post(
        db,
        [(ledger.CUSTOMER_FUNDS, payment.amount, 0), (ledger.WALLET_LIABILITY, 0, payment.amount)],
        "PAYMENT", payment.id, "Pembayaran terlambat dikembalikan ke saldo",
    )
    emit(db, "payment.refunded_to_wallet", payment_id=payment.id, user_id=payment.user_id, amount=payment.amount)


def cancel(db: Session, user, order_id: int) -> Order:
    order = db.get(Order, order_id, with_for_update=True)
    if order is None or order.user_id != user.id:
        raise ApiError("ORDER_NOT_FOUND", "Pesanan tidak ditemukan.")
    if order.status != "PENDING_PAYMENT":
        raise ApiError("INVALID_STATE", "Pesanan ini tidak bisa dibatalkan.")
    _release_order(db, order, "CANCELLED", "USER", user.id, "Dibatalkan pengguna")
    return order


def sweep_expired(db: Session) -> int:
    """Dipanggil worker: order lewat batas bayar dilepas, keping kembali tersedia."""
    ids = db.scalars(select(Order.id).where(Order.status == "PENDING_PAYMENT", Order.pay_before < clock.now())).all()
    for order_id in ids:
        order = db.get(Order, order_id, with_for_update=True)
        if order.status == "PENDING_PAYMENT":
            _release_order(db, order, "EXPIRED", "SYSTEM", None, "Batas bayar habis")
    return len(ids)


def sell(db: Session, user, quote_id: str, token_codes: list[str], pin: str | None) -> tuple[Order, list[Token]]:
    verify_pin(db, user, pin)
    require_verified(user)
    codes = list(dict.fromkeys(c.strip().upper() for c in token_codes))
    if not codes:
        raise ApiError("INVALID", "Pilih minimal satu token.")
    quote = pricing.consume_quote(db, user, quote_id, "SELL")
    if quote.quantity != len(codes):
        raise ApiError("INVALID", "Jumlah token tidak sesuai harga yang dikunci.")
    sku = db.get(Sku, quote.sku_id)
    pricing.ensure_trading_open(db, sku)

    # Urutan kunci: units → tokens → wallets.
    unit_ids = db.scalars(select(Token.unit_id).where(Token.token_code.in_(codes), Token.user_id == user.id)).all()
    units = inventory.lock_units(db, list(unit_ids))
    owned = tokens.lock_user_tokens(db, user.id, codes)
    holding = timedelta(seconds=get_settings().holding_period_seconds)
    now = clock.now()
    for token in owned:
        if token.sku_id != sku.id:
            raise ApiError("INVALID", "Token tidak sesuai produk yang dikunci harganya.")
        if token.status != "ACTIVE":
            raise ApiError("TOKEN_LOCKED", "Token ini sedang terkunci dan tidak bisa dijual.")
        if now - token.created_at < holding:
            hours = int(holding.total_seconds() // 3600)
            raise ApiError("HOLDING_PERIOD_ACTIVE", f"Token baru bisa dijual {hours} jam setelah pembelian.")
        if token.unit_id not in units:
            raise ApiError("CONFLICT", "Data token berubah. Coba lagi.")

    gross = quote.total
    tax = ledger.sell_tax(db, gross)
    order = Order(
        user_id=user.id, side="SELL", status="COMPLETED", payment_method="WALLET", quote_id=quote.id, sku_id=sku.id,
        quantity=quote.quantity, unit_price=quote.unit_price, gross=gross, tax=tax, net=gross - tax, completed_at=now,
    )
    db.add(order)
    db.flush()
    for token in owned:
        tokens.transition(db, token, "RETURNED", "USER", user.id, f"Dijual, order #{order.id}")
        unit = units[token.unit_id]
        if unit.status == "ALLOCATED":
            inventory.set_status(db, unit, "AVAILABLE", "USER", user.id, f"Token {token.token_code} dijual kembali")
        db.add(OrderItem(order_id=order.id, unit_id=unit.id, token_id=token.id))

    wallet.credit(db, user.id, order.net, "SALE_CREDIT", "ORDER", order.id, withdrawable_at=now)
    ledger.post(
        db,
        [
            (ledger.GOLD_BUYBACK, gross, 0),
            (ledger.WALLET_LIABILITY, 0, order.net),
            (ledger.TAX_PAYABLE, 0, tax),
            (ledger.CUSTOMER_FUNDS, order.net, 0),
            (ledger.OPERATING, 0, order.net),
        ],
        "ORDER", order.id, "Jual emas, dana masuk saldo",
    )
    emit(db, "order.sold", order_id=order.id, user_id=user.id, tokens=codes)
    return order, owned
