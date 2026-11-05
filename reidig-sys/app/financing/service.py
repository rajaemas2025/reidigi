"""Cicil emas lewat mitra (spesifikasi 6.3). Token baru terbit setelah uang muka lunas."""

import json
import secrets
from datetime import timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.catalog.models import Sku
from app.core import clock
from app.core.config import get_settings
from app.core.errors import ApiError
from app.core.models import emit
from app.core.security import verify_hmac
from app.financing.models import (
    FinancingAgreement,
    FinancingAgreementVersion,
    FinancingApplication,
    FinancingContract,
    FinancingEvent,
)
from app.identity.service import require_verified, verify_pin
from app.inventory import service as inventory
from app.inventory.models import Unit
from app.ledger import service as ledger
from app.payments import service as payments
from app.payments.models import Payment
from app.pricing import service as pricing
from app.tokens import service as tokens
from app.tokens.models import Token
from app.wallet import service as wallet


def current_agreement(db: Session) -> FinancingAgreementVersion:
    version = db.scalar(select(FinancingAgreementVersion).where(FinancingAgreementVersion.is_current.is_(True)))
    if version is None:
        raise ApiError("FEATURE_DISABLED", "Cicilan belum tersedia.")
    return version


def simulate(db: Session, sku_code: str, down_payment: int, tenor_months: int) -> dict:
    """Belum ada yang berubah di sistem. Bunga dan biaya mitra belum diketahui."""
    sku = pricing.sku_by_code(db, sku_code)
    pricing.ensure_trading_open(db, sku)
    price = pricing.latest_price(db, sku.id).buy_price
    if tenor_months not in get_settings().financing_tenors:
        raise ApiError("INVALID", "Tenor tidak tersedia.")
    if not 0 < down_payment < price:
        raise ApiError("INVALID", "Uang muka harus lebih dari 0 dan kurang dari harga.")
    return {
        "sku_code": sku.code,
        "price": price,
        "down_payment": down_payment,
        "tenor_months": tenor_months,
        "monthly_before_fees": (price - down_payment) // tenor_months,
        "partner": get_settings().financing_partner_name,
    }


def apply(
    db: Session, user, *, sku_code: str, down_payment: int, tenor_months: int, expected_price: int,
    agreement_version: str, agreement_hash: str, agreed_terms: bool, agreed_data_sharing: bool,
    pin: str | None, device_id: str | None, ip: str | None, user_agent: str | None,
) -> FinancingApplication:
    verify_pin(db, user, pin)
    require_verified(user)
    agreement = current_agreement(db)
    if not (agreed_terms and agreed_data_sharing) or agreement_version != agreement.version or agreement_hash != agreement.content_hash:
        raise ApiError("AGREEMENT_REQUIRED", "Setujui perjanjian cicilan terlebih dahulu.")
    sim = simulate(db, sku_code, down_payment, tenor_months)
    if sim["price"] != expected_price:
        raise ApiError("PRICE_CHANGED", "Harga sudah berubah. Silakan periksa lagi perjanjiannya.")

    sku = pricing.sku_by_code(db, sku_code)
    proof = FinancingAgreement(
        user_id=user.id, version=agreement.version, content_hash=agreement.content_hash, sku_code=sku.code,
        price=sim["price"], down_payment=down_payment, tenor_months=tenor_months, monthly_before_fees=sim["monthly_before_fees"],
        agreed_terms=True, agreed_data_sharing=True, device_id=device_id, ip_address=ip, user_agent=user_agent,
    )
    db.add(proof)
    db.flush()
    [unit] = inventory.reserve_units(db, sku.id, 1, "USER", user.id, "Pengajuan cicilan")
    application = FinancingApplication(
        user_id=user.id, sku_id=sku.id, unit_id=unit.id, agreement_id=proof.id, price=sim["price"],
        down_payment=down_payment, tenor_months=tenor_months, monthly_before_fees=sim["monthly_before_fees"], status="RESERVED",
        pay_before=clock.now() + timedelta(seconds=get_settings().payment_window_seconds),
    )
    db.add(application)
    db.flush()
    return application


def _application(db: Session, user, application_id: int) -> FinancingApplication:
    application = db.get(FinancingApplication, application_id, with_for_update=True)
    if application is None or application.user_id != user.id:
        raise ApiError("ORDER_NOT_FOUND", "Pengajuan tidak ditemukan.")
    return application


def _close(db: Session, application: FinancingApplication, status: str, actor_type: str, actor_id, reason: str) -> None:
    unit = db.get(Unit, application.unit_id, with_for_update=True)
    if unit.status == "RESERVED":
        inventory.set_status(db, unit, "AVAILABLE", actor_type, actor_id, reason)
    application.status = status
    application.closed_at = clock.now()
    payments.close_pending(db, "FINANCING_DP", application.id, "EXPIRED" if status == "EXPIRED" else "CANCELLED")


def _expire_if_due(db: Session, application: FinancingApplication) -> None:
    if application.status == "RESERVED" and clock.now() > application.pay_before:
        _close(db, application, "EXPIRED", "SYSTEM", None, "Batas bayar uang muka habis")


def pay_down_payment(db: Session, user, application_id: int, method: str, pin: str | None):
    """Saldo kurang tidak membatalkan pengajuan; pengguna bisa memilih metode lain."""
    if method == "WALLET":
        verify_pin(db, user, pin)
    application = _application(db, user, application_id)
    _expire_if_due(db, application)
    if application.status == "EXPIRED":
        db.commit()
        raise ApiError("ORDER_EXPIRED", "Batas waktu pembayaran habis. Pengajuan dibatalkan.")
    if application.status != "RESERVED":
        raise ApiError("INVALID_STATE", "Pengajuan ini tidak menunggu uang muka.")

    if method == "WALLET":
        unit = db.get(Unit, application.unit_id, with_for_update=True)
        wallet.debit(db, user.id, application.down_payment, "PURCHASE_DEBIT", "FINANCING_DP", application.id)
        contract = _finalize(db, application, unit, "USER", user.id, [
            (ledger.WALLET_LIABILITY, application.down_payment, 0),
            (ledger.OPERATING, application.down_payment, 0),
            (ledger.CUSTOMER_FUNDS, 0, application.down_payment),
        ])
        return contract, None
    if method not in ("QRIS", "VIRTUAL_ACCOUNT"):
        raise ApiError("INVALID", "Metode pembayaran tidak dikenal.")
    existing = db.scalar(
        select(Payment).where(
            Payment.purpose == "FINANCING_DP", Payment.reference_id == application.id, Payment.method == method, Payment.status == "PENDING"
        )
    )
    payment = existing or payments.create_payment(
        db, user_id=user.id, purpose="FINANCING_DP", reference_id=application.id, method=method,
        amount=application.down_payment, expires_at=application.pay_before,
    )
    return None, payment


def _finalize(db: Session, application: FinancingApplication, unit: Unit, actor_type: str, actor_id, dp_lines) -> FinancingContract:
    sku = db.get(Sku, application.sku_id)
    inventory.set_status(db, unit, "ALLOCATED", actor_type, actor_id, f"Uang muka cicilan #{application.id} lunas")
    token = tokens.mint(
        db, user_id=application.user_id, unit=unit, sku=sku, status="LOCKED_INSTALLMENT", price=application.price,
        origin_type="FINANCING", origin_id=application.id, actor_type=actor_type, actor_id=actor_id,
    )
    financed = application.price - application.down_payment
    ledger.post(
        db,
        [*dp_lines, (ledger.FINANCING_RECEIVABLE, financed, 0), (ledger.GOLD_SALES, 0, application.price)],
        "FINANCING", application.id, "Penjualan emas cicilan",
    )
    proof = db.get(FinancingAgreement, application.agreement_id)
    contract = FinancingContract(
        contract_ref=f"MTR-{secrets.token_hex(5).upper()}", application_id=application.id, user_id=application.user_id,
        partner=get_settings().financing_partner_name, token_id=token.id, price=application.price,
        down_payment=application.down_payment, tenor_months=application.tenor_months, paid_months=0, status="ON_TIME",
        agreement_version=proof.version, agreed_at=proof.agreed_at,
    )
    db.add(contract)
    application.status = "PAID"
    application.closed_at = clock.now()
    payments.close_pending(db, "FINANCING_DP", application.id, "CANCELLED")
    db.flush()
    emit(db, "financing.contract_created", contract_id=contract.id, user_id=application.user_id)
    return contract


def on_down_payment_paid(db: Session, payment: Payment) -> None:
    from app.orders.service import refund_late_payment

    application = db.get(FinancingApplication, payment.reference_id, with_for_update=True)
    _expire_if_due(db, application)
    if application.status != "RESERVED":
        refund_late_payment(db, payment)
        return
    payment.status = "PAID"
    unit = db.get(Unit, application.unit_id, with_for_update=True)
    _finalize(db, application, unit, "GATEWAY", payment.provider_ref, [(ledger.PG_CLEARING, application.down_payment, 0)])


def cancel(db: Session, user, application_id: int) -> FinancingApplication:
    application = _application(db, user, application_id)
    if application.status != "RESERVED":
        raise ApiError("INVALID_STATE", "Pengajuan ini tidak bisa dibatalkan.")
    _close(db, application, "CANCELLED", "USER", user.id, "Pengajuan cicilan dibatalkan")
    return application


def sweep_expired(db: Session) -> int:
    ids = db.scalars(
        select(FinancingApplication.id).where(FinancingApplication.status == "RESERVED", FinancingApplication.pay_before < clock.now())
    ).all()
    for application_id in ids:
        _expire_if_due(db, db.get(FinancingApplication, application_id, with_for_update=True))
    return len(ids)


def handle_partner_webhook(db: Session, body: bytes, signature: str | None) -> FinancingEvent:
    """
    Isi webhook (asumsi, sesuaikan dengan kontrak mitra):
    {"event_id", "contract_ref", "type": INSTALLMENT_PAID | PAID_OFF | OVERDUE | DEFAULTED, "paid_months"}
    """
    if not verify_hmac(get_settings().financing_partner_secret, body, signature):
        raise ApiError("INVALID_SIGNATURE", "Tanda tangan tidak valid.")
    try:
        data = json.loads(body)
        event_id, contract_ref, type_ = str(data["event_id"]), str(data["contract_ref"]), str(data["type"])
    except (ValueError, KeyError, TypeError):
        raise ApiError("INVALID", "Isi webhook tidak valid.")
    existing = db.scalar(select(FinancingEvent).where(FinancingEvent.event_id == event_id))
    if existing:
        return existing
    contract = db.scalar(select(FinancingContract).where(FinancingContract.contract_ref == contract_ref).with_for_update())
    if contract is None:
        raise ApiError("NOT_FOUND", "Kontrak tidak ditemukan.")
    if contract.status in ("PAID_OFF", "DEFAULTED"):
        raise ApiError("INVALID_STATE", "Kontrak sudah ditutup.")

    token = db.get(Token, contract.token_id)
    if type_ == "INSTALLMENT_PAID":
        contract.paid_months = min(contract.tenor_months, int(data.get("paid_months", contract.paid_months + 1)))
        contract.status = "ON_TIME"
    elif type_ == "OVERDUE":
        contract.status = "OVERDUE"
    elif type_ == "PAID_OFF":
        db.get(Unit, token.unit_id, with_for_update=True)
        db.refresh(token, with_for_update=True)
        tokens.transition(db, token, "ACTIVE", "PARTNER", event_id, f"Cicilan {contract.contract_ref} lunas")
        contract.paid_months = contract.tenor_months
        contract.status = "PAID_OFF"
        financed = contract.price - contract.down_payment
        ledger.post(db, [(ledger.OPERATING, financed, 0), (ledger.FINANCING_RECEIVABLE, 0, financed)], "FINANCING_CONTRACT", contract.id, "Pelunasan dari mitra")
    elif type_ == "DEFAULTED":
        # Penyelesaian keuangan dengan mitra saat gagal bayar menunggu kontrak; di sini hanya status token dan keping.
        unit = db.get(Unit, token.unit_id, with_for_update=True)
        db.refresh(token, with_for_update=True)
        tokens.transition(db, token, "CANCELLED", "PARTNER", event_id, f"Wanprestasi {contract.contract_ref}")
        if unit.status == "ALLOCATED":
            inventory.set_status(db, unit, "AVAILABLE", "PARTNER", event_id, "Token cicilan dibatalkan")
        contract.status = "DEFAULTED"
    else:
        raise ApiError("INVALID", "Jenis webhook tidak dikenal.")
    event = FinancingEvent(event_id=event_id, contract_id=contract.id, type=type_, payload=data)
    db.add(event)
    emit(db, "financing.updated", contract_id=contract.id, type=type_)
    return event
