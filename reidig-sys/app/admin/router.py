"""API dashboard internal. Hak akses mengikuti peran, sama dengan dashboard/src/api/core.ts."""

from datetime import datetime, timedelta

from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.admin.audit import audit
from app.admin.models import AdminSession, AdminUser, AuditLog
from app.catalog.models import Sku
from app.core import clock
from app.core.config import get_settings
from app.core.db import get_db
from app.core.deps import READ_ROLES, bearer, current_admin, roles
from app.core.errors import ApiError
from app.core.models import SystemFlag
from app.core.security import new_session_token, sha256_hex, verify_secret
from app.core.types import Rupiah, Schema
from app.financing.models import FinancingContract
from app.fulfillment import service as fulfillment
from app.fulfillment.models import PrintOrder
from app.fulfillment.router import PrintOut, print_out
from app.identity import service as identity
from app.identity.models import BankAccount, KycRecord, User
from app.inventory import service as inventory
from app.inventory.models import Location, StockCount, StockCountScan, StockCountVariance, Unit
from app.inventory.service import PHYSICALLY_PRESENT
from app.jobs import run_sweeps
from app.ledger import service as ledger
from app.pricing import service as pricing
from app.pricing.models import SkuPrice
from app.reconciliation import service as reconciliation
from app.tokens.models import FINAL_STATUSES, Token
from app.wallet import withdrawals
from app.wallet.models import Wallet, Withdrawal

router = APIRouter(prefix="/v1/admin", tags=["Admin"])

STAFF = ("WAREHOUSE_STAFF", "WAREHOUSE_SUPERVISOR")
SPV = ("WAREHOUSE_SUPERVISOR",)
readers = roles(*READ_ROLES)


# ----- Masuk -----


class LoginIn(Schema):
    email: str
    password: str


class AdminOut(Schema):
    id: int
    name: str
    email: str
    role: str


class AdminSessionOut(Schema):
    access_token: str
    admin: AdminOut


@router.post("/auth/login", response_model=AdminSessionOut)
def login(body: LoginIn, db: Session = Depends(get_db)):
    admin = db.scalar(select(AdminUser).where(AdminUser.email == body.email.strip().lower()))
    if admin is None or not admin.is_active or not verify_secret(body.password, admin.password_hash):
        raise ApiError("INVALID_CREDENTIALS", "Email atau kata sandi salah.")
    token = new_session_token()
    db.add(AdminSession(admin_id=admin.id, token_hash=sha256_hex(token), expires_at=clock.now() + timedelta(hours=12)))
    audit(db, admin, "ADMIN_LOGIN", admin.email)
    db.commit()
    return AdminSessionOut(access_token=token, admin=AdminOut.model_validate(admin))


@router.post("/auth/logout", status_code=204)
def logout(token: str = Depends(bearer), db: Session = Depends(get_db)):
    session = db.scalar(select(AdminSession).where(AdminSession.token_hash == sha256_hex(token)))
    if session:
        session.revoked_at = clock.now()
        db.commit()


@router.get("/me", response_model=AdminOut)
def me(admin: AdminUser = Depends(current_admin)):
    return AdminOut.model_validate(admin)


# ----- SKU dan harga -----


class SkuRow(Schema):
    id: int
    code: str
    brand: str
    product: str
    weight_mg: int
    is_active: bool
    trading_halted: bool
    halt_reason: str | None
    buy_price: Rupiah | None
    buyback_price: Rupiah | None
    available: int
    locked: int
    other: int


class SkuIn(Schema):
    code: str
    brand: str = "Raja Emas"
    product: str
    weight_mg: int
    buy_price: Rupiah
    buyback_price: Rupiah


class SkuPatch(Schema):
    is_active: bool


class PriceIn(Schema):
    sku_code: str
    buy_price: Rupiah
    buyback_price: Rupiah


class PricesIn(Schema):
    source: str = "MANUAL"
    prices: list[PriceIn]


def sku_rows(db: Session) -> list[SkuRow]:
    from app.catalog.router import latest_prices

    prices = latest_prices(db)
    bound = set(db.scalars(select(Token.unit_id).where(Token.status.not_in(FINAL_STATUSES))))
    rows = []
    for sku in db.scalars(select(Sku).order_by(Sku.weight_mg, Sku.code)):
        units = db.scalars(select(Unit).where(Unit.sku_id == sku.id)).all()
        available = sum(1 for u in units if u.status == "AVAILABLE")
        locked = sum(1 for u in units if u.id in bound)
        present = sum(1 for u in units if u.status in PHYSICALLY_PRESENT)
        p = prices.get(sku.id)
        rows.append(
            SkuRow(
                id=sku.id, code=sku.code, brand=sku.brand, product=sku.product, weight_mg=sku.weight_mg, is_active=sku.is_active,
                trading_halted=sku.trading_halted, halt_reason=sku.halt_reason, buy_price=p.buy_price if p else None,
                buyback_price=p.buyback_price if p else None, available=available, locked=locked, other=present - available - locked,
            )
        )
    return rows


@router.get("/skus", response_model=list[SkuRow], dependencies=[Depends(readers)])
def list_skus(db: Session = Depends(get_db)):
    return sku_rows(db)


@router.post("/skus", response_model=SkuRow, status_code=201)
def create_sku(body: SkuIn, admin: AdminUser = Depends(roles(*SPV)), db: Session = Depends(get_db)):
    code = body.code.strip().upper()
    if not code:
        raise ApiError("INVALID", "Kode SKU wajib diisi.")
    if db.scalar(select(Sku.id).where(Sku.code == code)):
        raise ApiError("DUPLICATE", "Kode SKU sudah dipakai.")
    if body.weight_mg <= 0:
        raise ApiError("INVALID", "Berat harus lebih dari 0.")
    db.add(Sku(code=code, brand=body.brand, product=body.product, weight_mg=body.weight_mg))
    db.flush()
    pricing.record_prices(db, None, "MANUAL", [(code, body.buy_price, body.buyback_price)])
    audit(db, admin, "SKU_CREATED", code, f"{body.product}, {body.weight_mg} mg")
    db.commit()
    return next(r for r in sku_rows(db) if r.code == code)


@router.patch("/skus/{sku_id}", response_model=SkuRow)
def update_sku(sku_id: int, body: SkuPatch, admin: AdminUser = Depends(roles(*SPV)), db: Session = Depends(get_db)):
    sku = db.get(Sku, sku_id)
    if sku is None:
        raise ApiError("NOT_FOUND", "SKU tidak ditemukan.")
    sku.is_active = body.is_active
    audit(db, admin, "SKU_UPDATED", sku.code, f"is_active={body.is_active}")
    db.commit()
    return next(r for r in sku_rows(db) if r.id == sku_id)


@router.post("/prices", status_code=201)
def set_prices(body: PricesIn, admin: AdminUser = Depends(roles("TREASURY")), db: Session = Depends(get_db)):
    run = pricing.record_prices(db, admin, body.source, [(p.sku_code, p.buy_price, p.buyback_price) for p in body.prices])
    db.commit()
    return {"run_id": run.id, "count": len(body.prices)}


# ----- Perdagangan -----


class TradingOut(Schema):
    halted: bool
    reason: str | None
    halted_by: str | None
    halted_by_system: bool
    withdrawals_frozen: bool
    withdrawals_frozen_reason: str | None
    price_updated_at: datetime | None
    halted_skus: list[str]


class HaltIn(Schema):
    reason: str
    sku_code: str | None = None


class ResumeIn(Schema):
    sku_code: str | None = None


@router.get("/trading", response_model=TradingOut, dependencies=[Depends(readers)])
def trading(db: Session = Depends(get_db)):
    halted = pricing.flag(db, pricing.TRADING_HALTED)
    frozen = pricing.flag(db, pricing.WITHDRAWALS_FROZEN)
    by = db.get(AdminUser, halted.set_by_admin_id) if halted.set_by_admin_id else None
    db.commit()
    return TradingOut(
        halted=halted.enabled, reason=halted.reason, halted_by=by.name if by else None, halted_by_system=halted.set_by_system,
        withdrawals_frozen=frozen.enabled, withdrawals_frozen_reason=frozen.reason,
        price_updated_at=db.scalar(select(func.max(SkuPrice.fetched_at))),
        halted_skus=list(db.scalars(select(Sku.code).where(Sku.trading_halted.is_(True)))),
    )


@router.post("/trading/halt", status_code=204)
def halt(body: HaltIn, admin: AdminUser = Depends(roles("TREASURY")), db: Session = Depends(get_db)):
    pricing.halt_trading(db, admin, body.reason, body.sku_code)
    db.commit()


@router.post("/trading/resume", status_code=204)
def resume(body: ResumeIn, admin: AdminUser = Depends(roles("TREASURY")), db: Session = Depends(get_db)):
    pricing.resume_trading(db, admin, body.sku_code)
    db.commit()


@router.post("/withdrawals/unfreeze", status_code=204)
def unfreeze(admin: AdminUser = Depends(roles("FINANCE")), db: Session = Depends(get_db)):
    """Dibuka setelah rekonsiliasi kembali bersih."""
    failed = [r.id for r in reconciliation.check_invariants(db) if not r.ok and r.id in ("INV-9", "INV-10", "INV-10B")]
    if failed:
        raise ApiError("INVALID_STATE", f"Invarian saldo masih dilanggar: {', '.join(failed)}")
    item = pricing.flag(db, pricing.WITHDRAWALS_FROZEN)
    item.enabled, item.reason, item.set_by_admin_id, item.set_by_system, item.updated_at = False, None, admin.id, False, clock.now()
    audit(db, admin, "WITHDRAWALS_UNFROZEN", "Penarikan")
    db.commit()


# ----- Lokasi dan keping -----


class LocationOut(Schema):
    id: int
    code: str
    name: str
    type: str


class UnitRow(Schema):
    id: int
    serial_number: str
    sku_id: int
    sku_code: str
    product: str
    weight_mg: int
    location_id: int
    location_name: str
    status: str
    token_code: str | None


class ReceiveIn(Schema):
    sku_id: int
    location_id: int
    serials: list[str]
    note: str | None = None


class QcIn(Schema):
    passed: bool
    note: str | None = None


class ReasonIn(Schema):
    reason: str


class QuarantineOut(Schema):
    unit: UnitRow
    replacement_serial: str | None


def unit_row(db: Session, u: Unit, token_by_unit: dict[int, str] | None = None) -> UnitRow:
    sku = db.get(Sku, u.sku_id)
    if token_by_unit is None:
        token = inventory.open_token_for_unit(db, u.id)
        token_code = token.token_code if token else None
    else:
        token_code = token_by_unit.get(u.id)
    return UnitRow(
        id=u.id, serial_number=u.serial_number, sku_id=u.sku_id, sku_code=sku.code, product=sku.product, weight_mg=sku.weight_mg,
        location_id=u.location_id, location_name=db.get(Location, u.location_id).name, status=u.status, token_code=token_code,
    )


@router.get("/locations", response_model=list[LocationOut], dependencies=[Depends(readers)])
def locations(db: Session = Depends(get_db)):
    return [LocationOut.model_validate(loc) for loc in db.scalars(select(Location).order_by(Location.id))]


@router.get("/units", response_model=list[UnitRow], dependencies=[Depends(readers)])
def list_units(
    sku_id: int | None = None, location_id: int | None = None, status: str | None = None, q: str | None = None,
    limit: int = 500, db: Session = Depends(get_db),
):
    token_by_unit = dict(db.execute(select(Token.unit_id, Token.token_code).where(Token.status.not_in(FINAL_STATUSES))).all())
    query = select(Unit).order_by(Unit.id)
    if sku_id:
        query = query.where(Unit.sku_id == sku_id)
    if location_id:
        query = query.where(Unit.location_id == location_id)
    if status:
        query = query.where(Unit.status == status)
    if q and q.strip():
        needle = q.strip().upper()
        unit_ids = [uid for uid, code in token_by_unit.items() if needle in code]
        query = query.where(Unit.serial_number.contains(needle) | Unit.id.in_(unit_ids))
    return [unit_row(db, u, token_by_unit) for u in db.scalars(query.limit(min(limit, 2000)))]


@router.post("/units/receive", status_code=201)
def receive(body: ReceiveIn, admin: AdminUser = Depends(roles(*STAFF)), db: Session = Depends(get_db)):
    batch = inventory.receive(db, admin, body.sku_id, body.location_id, body.serials, body.note)
    db.commit()
    return {"batch_id": batch.id, "received": batch.quantity}


@router.post("/units/{unit_id}/qc", response_model=UnitRow)
def qc(unit_id: int, body: QcIn, admin: AdminUser = Depends(roles(*SPV)), db: Session = Depends(get_db)):
    unit = inventory.decide_qc(db, admin, unit_id, body.passed, body.note)
    db.commit()
    return unit_row(db, unit)


@router.post("/units/{unit_id}/quarantine", response_model=QuarantineOut)
def quarantine(unit_id: int, body: ReasonIn, admin: AdminUser = Depends(roles(*SPV)), db: Session = Depends(get_db)):
    replacement = inventory.quarantine(db, admin, unit_id, body.reason)
    db.commit()
    return QuarantineOut(unit=unit_row(db, db.get(Unit, unit_id)), replacement_serial=replacement.serial_number if replacement else None)


@router.post("/units/{unit_id}/release", response_model=UnitRow)
def release(unit_id: int, admin: AdminUser = Depends(roles(*SPV)), db: Session = Depends(get_db)):
    unit = inventory.release_quarantine(db, admin, unit_id)
    db.commit()
    return unit_row(db, unit)


@router.post("/units/{unit_id}/write-off", response_model=UnitRow)
def write_off(unit_id: int, body: ReasonIn, admin: AdminUser = Depends(roles("FINANCE")), db: Session = Depends(get_db)):
    unit = inventory.write_off(db, admin, unit_id, body.reason)
    db.commit()
    return unit_row(db, unit)


# ----- Stock opname -----


class ScanOut(Schema):
    serial_number: str
    scanned_by: str
    created_at: datetime


class VarianceOut(Schema):
    id: int
    type: str
    serial_number: str
    unit_id: int | None
    recount_found: bool | None
    recount_by: str | None


class CountOut(Schema):
    id: int
    location_id: int
    location_name: str
    status: str
    created_by: str
    approved_by: str | None
    created_at: datetime
    expected_unit_ids: list[int]
    scans: list[ScanOut]
    variances: list[VarianceOut]


class ScanIn(Schema):
    serial_number: str


class ScanResult(Schema):
    duplicate: bool
    total: int


class CountIn(Schema):
    location_id: int


class RecountIn(Schema):
    found: bool


def _name(db: Session, admin_id: int | None) -> str | None:
    return db.get(AdminUser, admin_id).name if admin_id else None


def count_out(db: Session, c: StockCount) -> CountOut:
    scans = db.scalars(select(StockCountScan).where(StockCountScan.count_id == c.id).order_by(StockCountScan.id))
    variances = db.scalars(select(StockCountVariance).where(StockCountVariance.count_id == c.id).order_by(StockCountVariance.id))
    return CountOut(
        id=c.id, location_id=c.location_id, location_name=db.get(Location, c.location_id).name, status=c.status,
        created_by=_name(db, c.created_by_admin_id), approved_by=_name(db, c.approved_by_admin_id), created_at=c.created_at,
        # Hitung buta: daftar harapan disembunyikan selama menghitung.
        expected_unit_ids=[] if c.status == "COUNTING" else c.expected_ids,
        scans=[ScanOut(serial_number=s.serial_number, scanned_by=_name(db, s.scanned_by_admin_id), created_at=s.created_at) for s in scans],
        variances=[
            VarianceOut(id=v.id, type=v.type, serial_number=v.serial_number, unit_id=v.unit_id, recount_found=v.recount_found, recount_by=_name(db, v.recount_by_admin_id))
            for v in variances
        ],
    )


@router.get("/counts", response_model=list[CountOut], dependencies=[Depends(readers)])
def list_counts(db: Session = Depends(get_db)):
    return [count_out(db, c) for c in db.scalars(select(StockCount).order_by(StockCount.id.desc()))]


@router.get("/counts/{count_id}", response_model=CountOut, dependencies=[Depends(readers)])
def get_count(count_id: int, db: Session = Depends(get_db)):
    count = db.get(StockCount, count_id)
    if count is None:
        raise ApiError("NOT_FOUND", "Sesi opname tidak ditemukan.")
    return count_out(db, count)


@router.post("/counts", response_model=CountOut, status_code=201)
def create_count(body: CountIn, admin: AdminUser = Depends(roles(*SPV)), db: Session = Depends(get_db)):
    count = inventory.create_count(db, admin, body.location_id)
    db.commit()
    return count_out(db, count)


@router.post("/counts/{count_id}/scans", response_model=ScanResult)
def scan(count_id: int, body: ScanIn, admin: AdminUser = Depends(roles(*STAFF)), db: Session = Depends(get_db)):
    duplicate, total = inventory.scan(db, admin, count_id, body.serial_number)
    db.commit()
    return ScanResult(duplicate=duplicate, total=total)


@router.post("/counts/{count_id}/submit", response_model=CountOut)
def submit(count_id: int, admin: AdminUser = Depends(roles(*STAFF)), db: Session = Depends(get_db)):
    count = inventory.submit_count(db, admin, count_id)
    db.commit()
    return count_out(db, count)


@router.post("/counts/{count_id}/variances/{variance_id}/recount", response_model=CountOut)
def recount(count_id: int, variance_id: int, body: RecountIn, admin: AdminUser = Depends(roles(*STAFF)), db: Session = Depends(get_db)):
    count = inventory.record_recount(db, admin, count_id, variance_id, body.found)
    db.commit()
    return count_out(db, count)


@router.post("/counts/{count_id}/approve", response_model=CountOut)
def approve(count_id: int, admin: AdminUser = Depends(roles("WAREHOUSE_SUPERVISOR", "FINANCE")), db: Session = Depends(get_db)):
    count = inventory.approve_count(db, admin, count_id)
    db.commit()
    return count_out(db, count)


# ----- Pemenuhan cetak -----


class AdminPrintOut(PrintOut):
    owner: str


class SerialIn(Schema):
    serial_number: str


class ShipIn(Schema):
    tracking_number: str
    carrier: str = "JNE"


class PickupIn(Schema):
    code: str
    identity_checked: bool


def admin_print_out(db: Session, order: PrintOrder) -> AdminPrintOut:
    # Kode ambil hanya untuk pengguna; petugas cabang harus memintanya.
    base = print_out(db, order, show_code=False)
    return AdminPrintOut(**base.model_dump(), owner=db.get(User, order.user_id).name)


@router.get("/print-orders", response_model=list[AdminPrintOut], dependencies=[Depends(readers)])
def list_print_orders(status: str | None = None, db: Session = Depends(get_db)):
    query = select(PrintOrder).order_by(PrintOrder.id.desc())
    if status:
        query = query.where(PrintOrder.status == status)
    return [admin_print_out(db, o) for o in db.scalars(query)]


@router.post("/print-orders/{order_id}/pick", response_model=AdminPrintOut)
def pick(order_id: int, body: SerialIn, admin: AdminUser = Depends(roles(*STAFF)), db: Session = Depends(get_db)):
    order = fulfillment.pick(db, admin, order_id, body.serial_number)
    db.commit()
    return admin_print_out(db, order)


@router.post("/print-orders/{order_id}/ship", response_model=AdminPrintOut)
def ship(order_id: int, body: ShipIn, admin: AdminUser = Depends(roles(*STAFF)), db: Session = Depends(get_db)):
    order = fulfillment.ship(db, admin, order_id, body.tracking_number, body.carrier)
    db.commit()
    return admin_print_out(db, order)


@router.post("/print-orders/{order_id}/arrive", response_model=AdminPrintOut)
def arrive(order_id: int, body: SerialIn, admin: AdminUser = Depends(roles(*STAFF)), db: Session = Depends(get_db)):
    order = fulfillment.receive_at_branch(db, admin, order_id, body.serial_number)
    db.commit()
    return admin_print_out(db, order)


@router.post("/print-orders/{order_id}/complete", response_model=AdminPrintOut)
def complete(order_id: int, admin: AdminUser = Depends(roles(*STAFF)), db: Session = Depends(get_db)):
    order = fulfillment.complete_delivery(db, admin, order_id)
    db.commit()
    return admin_print_out(db, order)


@router.post("/print-orders/{order_id}/pickup", response_model=AdminPrintOut)
def pickup(order_id: int, body: PickupIn, admin: AdminUser = Depends(roles(*STAFF)), db: Session = Depends(get_db)):
    order = fulfillment.complete_pickup(db, admin, order_id, body.code, body.identity_checked)
    db.commit()
    return admin_print_out(db, order)


@router.post("/print-orders/{order_id}/cancel", response_model=AdminPrintOut)
def cancel_print(order_id: int, body: ReasonIn, admin: AdminUser = Depends(roles(*SPV)), db: Session = Depends(get_db)):
    order = fulfillment.cancel_by_admin(db, admin, order_id, body.reason)
    db.commit()
    return admin_print_out(db, order)


# ----- Keuangan -----


class AdminWithdrawalOut(Schema):
    id: int
    owner: str
    bank: str
    amount: Rupiah
    status: str
    attempts: int
    created_at: datetime


class FinanceOut(Schema):
    wallet_total: Rupiah
    ledger_liability: Rupiah
    customer_funds_balance: Rupiah


class ResolveIn(Schema):
    succeeded: bool
    note: str


def admin_withdrawal_out(db: Session, w: Withdrawal) -> AdminWithdrawalOut:
    account = db.get(BankAccount, w.bank_account_id)
    return AdminWithdrawalOut(
        id=w.id, owner=db.get(User, w.user_id).name, bank=f"{account.bank_code} •••• {account.account_number[-4:]}",
        amount=w.amount, status=w.status, attempts=w.attempts, created_at=w.created_at,
    )


@router.get("/withdrawals", response_model=list[AdminWithdrawalOut], dependencies=[Depends(readers)])
def list_withdrawals(db: Session = Depends(get_db)):
    return [admin_withdrawal_out(db, w) for w in db.scalars(select(Withdrawal).order_by(Withdrawal.id.desc()))]


@router.post("/withdrawals/{withdrawal_id}/retry", response_model=AdminWithdrawalOut)
def retry_withdrawal(withdrawal_id: int, admin: AdminUser = Depends(roles("FINANCE")), db: Session = Depends(get_db)):
    w = withdrawals.retry(db, admin, withdrawal_id)
    db.commit()
    return admin_withdrawal_out(db, w)


@router.post("/withdrawals/{withdrawal_id}/resolve", response_model=AdminWithdrawalOut)
def resolve_withdrawal(withdrawal_id: int, body: ResolveIn, admin: AdminUser = Depends(roles("FINANCE")), db: Session = Depends(get_db)):
    w = withdrawals.resolve_unknown(db, admin, withdrawal_id, body.succeeded, body.note)
    db.commit()
    return admin_withdrawal_out(db, w)


@router.get("/finance", response_model=FinanceOut, dependencies=[Depends(readers)])
def finance(db: Session = Depends(get_db)):
    total = db.scalar(select(func.coalesce(func.sum(Wallet.available + Wallet.held), 0)))
    return FinanceOut(
        wallet_total=int(total), ledger_liability=ledger.balance(db, ledger.WALLET_LIABILITY),
        customer_funds_balance=ledger.balance(db, ledger.CUSTOMER_FUNDS),
    )


# ----- Invarian dan job -----


class InvariantOut(Schema):
    id: str
    label: str
    ok: bool
    detail: str


@router.get("/invariants", response_model=list[InvariantOut], dependencies=[Depends(readers)])
def invariants(db: Session = Depends(get_db)):
    return [InvariantOut(id=r.id, label=r.label, ok=r.ok, detail=r.detail) for r in reconciliation.check_invariants(db)]


@router.post("/reconciliation/run", response_model=list[InvariantOut])
def run_reconciliation(admin: AdminUser = Depends(roles("FINANCE", "TREASURY", "WAREHOUSE_SUPERVISOR")), db: Session = Depends(get_db)):
    results = reconciliation.run(db, admin)
    db.commit()
    return [InvariantOut(id=r.id, label=r.label, ok=r.ok, detail=r.detail) for r in results]


@router.post("/jobs/sweep")
def sweep(admin: AdminUser = Depends(roles("FINANCE", "TREASURY", "WAREHOUSE_SUPERVISOR")), db: Session = Depends(get_db)):
    """Untuk dev tanpa Celery: jalankan penyapuan order dan cicilan kedaluwarsa."""
    return run_sweeps(db)


# ----- KYC, cicilan, audit -----


class KycOut(Schema):
    id: int
    applicant: str
    nik_masked: str
    reason: str | None
    status: str
    decision_note: str | None
    created_at: datetime


class KycDecisionIn(Schema):
    approve: bool
    note: str = ""


class AdminContractOut(Schema):
    id: int
    contract_ref: str
    partner: str
    owner: str
    token_code: str
    price: Rupiah
    tenor_months: int
    paid_months: int
    status: str


class AuditOut(Schema):
    id: int
    created_at: datetime
    actor_name: str
    action: str
    entity: str
    detail: str


class AlertsOut(Schema):
    waiting_print: int
    pending_kyc: int
    problem_withdrawals: int
    failed_invariants: int


def kyc_out(db: Session, k: KycRecord) -> KycOut:
    return KycOut(
        id=k.id, applicant=k.full_name, nik_masked="••••••••••••" + k.nik_last4, reason=k.reason, status=k.status,
        decision_note=k.decision_note, created_at=k.created_at,
    )


@router.get("/kyc", response_model=list[KycOut], dependencies=[Depends(roles("COMPLIANCE", "CS", "AUDITOR"))])
def list_kyc(status: str | None = None, db: Session = Depends(get_db)):
    query = select(KycRecord).order_by(KycRecord.id.desc())
    if status:
        query = query.where(KycRecord.status == status)
    return [kyc_out(db, k) for k in db.scalars(query)]


@router.post("/kyc/{kyc_id}/decision", response_model=KycOut)
def decide_kyc(kyc_id: int, body: KycDecisionIn, admin: AdminUser = Depends(roles("COMPLIANCE")), db: Session = Depends(get_db)):
    record = identity.decide_kyc(db, admin, kyc_id, body.approve, body.note)
    db.commit()
    return kyc_out(db, record)


@router.get("/contracts", response_model=list[AdminContractOut], dependencies=[Depends(readers)])
def list_contracts(db: Session = Depends(get_db)):
    rows = db.scalars(select(FinancingContract).order_by(FinancingContract.id.desc()))
    return [
        AdminContractOut(
            id=c.id, contract_ref=c.contract_ref, partner=c.partner, owner=db.get(User, c.user_id).name,
            token_code=db.get(Token, c.token_id).token_code, price=c.price, tenor_months=c.tenor_months,
            paid_months=c.paid_months, status=c.status,
        )
        for c in rows
    ]


@router.get("/audit", response_model=list[AuditOut], dependencies=[Depends(readers)])
def list_audit(limit: int = 200, db: Session = Depends(get_db)):
    return [AuditOut.model_validate(a) for a in db.scalars(select(AuditLog).order_by(AuditLog.id.desc()).limit(min(limit, 1000)))]


@router.get("/alerts", response_model=AlertsOut, dependencies=[Depends(readers)])
def alerts(db: Session = Depends(get_db)):
    return AlertsOut(
        waiting_print=db.scalar(select(func.count()).where(PrintOrder.status == "WAITING")),
        pending_kyc=db.scalar(select(func.count()).where(KycRecord.status == "PENDING")),
        problem_withdrawals=db.scalar(select(func.count()).where(Withdrawal.status.in_(("FAILED", "UNKNOWN")))),
        failed_invariants=sum(1 for r in reconciliation.check_invariants(db) if not r.ok),
    )


@router.get("/flags", dependencies=[Depends(readers)])
def flags(db: Session = Depends(get_db)):
    return {f.key: {"enabled": f.enabled, "reason": f.reason} for f in db.scalars(select(SystemFlag))}


@router.get("/settings", dependencies=[Depends(readers)])
def settings_view():
    s = get_settings()
    return {
        "quote_ttl_seconds": s.quote_ttl_seconds,
        "payment_window_seconds": s.payment_window_seconds,
        "holding_period_seconds": s.holding_period_seconds,
        "pickup_min_weight_mg": s.pickup_min_weight_mg,
        "finance_approval_mg": s.finance_approval_mg,
        "topup_enabled": s.topup_enabled,
        "financing_tenors": s.financing_tenors,
    }
