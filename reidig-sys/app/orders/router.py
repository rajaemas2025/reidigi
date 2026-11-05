from datetime import datetime
from typing import Literal

from fastapi import APIRouter, Depends, Header
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.catalog.models import Sku
from app.core.db import get_db
from app.core.deps import current_user
from app.core.errors import ApiError
from app.core.idempotency import run_idempotent
from app.core.types import Rupiah, Schema
from app.financing.models import FinancingContract
from app.fulfillment.models import PrintOrder
from app.identity.models import User
from app.orders import service
from app.orders.models import Order
from app.payments.models import Payment
from app.pricing import service as pricing
from app.tokens.models import Token
from app.wallet.models import Withdrawal

router = APIRouter(prefix="/v1", tags=["Beli dan jual"])


class QuoteIn(Schema):
    sku_code: str
    quantity: int = 1
    side: Literal["BUY", "SELL"] = "BUY"


class QuoteOut(Schema):
    id: str
    sku_code: str
    side: str
    quantity: int
    unit_price: Rupiah
    total: Rupiah
    buyback_value_now: Rupiah
    expires_at: datetime


class BuyIn(Schema):
    quote_id: str
    payment_method: Literal["WALLET", "QRIS", "VIRTUAL_ACCOUNT"]


class SellIn(Schema):
    quote_id: str
    token_codes: list[str]
    pin: str


class PaymentOut(Schema):
    id: int
    method: str
    amount: Rupiah
    status: str
    payment_code: str
    expires_at: datetime


class OrderOut(Schema):
    id: int
    side: str
    status: str
    payment_method: str | None
    sku_code: str
    quantity: int
    unit_price: Rupiah
    gross: Rupiah
    tax: Rupiah
    net: Rupiah
    pay_before: datetime | None
    created_at: datetime
    token_codes: list[str]
    payment: PaymentOut | None


class TxOut(Schema):
    id: str
    type: Literal["BUY", "SELL", "WITHDRAW", "PRINT", "INSTALLMENT", "REFUND"]
    title: str
    amount: Rupiah
    status: str
    at: datetime


def quote_out(db: Session, q) -> QuoteOut:
    return QuoteOut(
        id=q.id, sku_code=db.get(Sku, q.sku_id).code, side=q.side, quantity=q.quantity, unit_price=q.unit_price,
        total=q.total, buyback_value_now=q.buyback_value_now, expires_at=q.expires_at,
    )


def order_out(db: Session, order: Order, payment: Payment | None = None) -> OrderOut:
    from app.orders.models import OrderItem

    codes = db.scalars(
        select(Token.token_code).join(OrderItem, OrderItem.token_id == Token.id).where(OrderItem.order_id == order.id).order_by(OrderItem.id)
    ).all()
    if payment is None and order.payment_method in ("QRIS", "VIRTUAL_ACCOUNT"):
        payment = db.scalar(select(Payment).where(Payment.purpose == "ORDER", Payment.reference_id == order.id).order_by(Payment.id.desc()).limit(1))
    return OrderOut(
        id=order.id, side=order.side, status=order.status, payment_method=order.payment_method, sku_code=db.get(Sku, order.sku_id).code,
        quantity=order.quantity, unit_price=order.unit_price, gross=order.gross, tax=order.tax, net=order.net,
        pay_before=order.pay_before, created_at=order.created_at, token_codes=list(codes),
        payment=PaymentOut.model_validate(payment) if payment else None,
    )


@router.post("/quotes", response_model=QuoteOut, status_code=201)
def create_quote(body: QuoteIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    quote = pricing.create_quote(db, user, body.sku_code, body.quantity, body.side)
    db.commit()
    return quote_out(db, quote)


@router.post("/orders/buy", response_model=OrderOut, status_code=201)
def buy(
    body: BuyIn,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    idempotency_key: str | None = Header(default=None),
):
    def action():
        order, _, payment = service.buy(db, user, body.quote_id, body.payment_method)
        return order_out(db, order, payment)

    return run_idempotent(db, f"user:{user.id}:buy", idempotency_key, body.model_dump(), OrderOut, action)


@router.post("/orders/sell", response_model=OrderOut, status_code=201)
def sell(
    body: SellIn,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    idempotency_key: str | None = Header(default=None),
):
    def action():
        order, _ = service.sell(db, user, body.quote_id, body.token_codes, body.pin)
        return order_out(db, order)

    payload = body.model_dump(exclude={"pin"})
    return run_idempotent(db, f"user:{user.id}:sell", idempotency_key, payload, OrderOut, action)


@router.get("/orders", response_model=list[OrderOut])
def list_orders(user: User = Depends(current_user), db: Session = Depends(get_db)):
    orders = db.scalars(select(Order).where(Order.user_id == user.id).order_by(Order.id.desc()).limit(100))
    return [order_out(db, o) for o in orders]


@router.get("/orders/{order_id}", response_model=OrderOut)
def get_order(order_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    order = db.get(Order, order_id)
    if order is None or order.user_id != user.id:
        raise ApiError("ORDER_NOT_FOUND", "Pesanan tidak ditemukan.")
    return order_out(db, order)


@router.post("/orders/{order_id}/cancel", response_model=OrderOut)
def cancel_order(order_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    order = service.cancel(db, user, order_id)
    db.commit()
    return order_out(db, order)


@router.get("/transactions", response_model=list[TxOut])
def transactions(user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Riwayat untuk layar aplikasi: gabungan pesanan, penarikan, cetak, dan cicilan."""
    out: list[TxOut] = []
    for o in db.scalars(select(Order).where(Order.user_id == user.id, Order.status.in_(("PAID", "COMPLETED", "PAID_AFTER_EXPIRY")))):
        sku = db.get(Sku, o.sku_id)
        if o.status == "PAID_AFTER_EXPIRY":
            out.append(TxOut(id=f"ORD-{o.id}", type="REFUND", title="Pembayaran terlambat dikembalikan ke saldo", amount=o.gross, status=o.status, at=o.completed_at or o.created_at))
        elif o.side == "BUY":
            out.append(TxOut(id=f"ORD-{o.id}", type="BUY", title=f"Beli {o.quantity} × {sku.product} {sku.code}", amount=-o.gross, status=o.status, at=o.completed_at or o.created_at))
        else:
            out.append(TxOut(id=f"ORD-{o.id}", type="SELL", title=f"Jual {o.quantity} × {sku.product} {sku.code}", amount=o.net, status=o.status, at=o.created_at))
    for w in db.scalars(select(Withdrawal).where(Withdrawal.user_id == user.id)):
        out.append(TxOut(id=f"WD-{w.id}", type="WITHDRAW", title="Tarik dana ke rekening", amount=-w.amount, status=w.status, at=w.created_at))
    for p in db.scalars(select(PrintOrder).where(PrintOrder.user_id == user.id)):
        title = "Cetak emas, ambil di cabang" if p.method == "PICKUP" else "Cetak emas, kirim JNE"
        out.append(TxOut(id=f"CTK-{p.id}", type="PRINT", title=title, amount=0, status=p.status, at=p.created_at))
    for c in db.scalars(select(FinancingContract).where(FinancingContract.user_id == user.id)):
        out.append(TxOut(id=f"{c.contract_ref}", type="INSTALLMENT", title=f"Uang muka cicil emas ({c.partner})", amount=-c.down_payment, status=c.status, at=c.created_at))
    return sorted(out, key=lambda t: t.at, reverse=True)
