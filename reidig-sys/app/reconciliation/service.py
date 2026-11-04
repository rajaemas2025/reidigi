"""
Pemeriksaan invarian (spesifikasi bagian 3) dan circuit breaker:
pelanggaran 1–3 menghentikan perdagangan SKU terkait, pelanggaran 9–10 membekukan penarikan dana.
"""

import logging
from dataclasses import dataclass, field

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.admin.audit import audit
from app.catalog.models import Sku
from app.core import clock
from app.core.models import emit
from app.inventory.models import Unit, UnitEvent
from app.inventory.service import LOCKED_UNIT
from app.ledger import service as ledger
from app.ledger.models import LedgerEntry
from app.pricing.service import WITHDRAWALS_FROZEN, flag
from app.tokens.models import FINAL_STATUSES, Token, TokenEvent
from app.tokens.service import event_hash
from app.wallet.models import Wallet, WalletEntry

log = logging.getLogger("reidig.reconciliation")


@dataclass
class Result:
    id: str
    label: str
    ok: bool
    detail: str
    sku_ids: set[int] = field(default_factory=set)


def check_invariants(db: Session) -> list[Result]:
    open_tokens = db.scalars(select(Token).where(Token.status.not_in(FINAL_STATUSES))).all()
    units = {u.id: u for u in db.scalars(select(Unit))}
    results: list[Result] = []

    # 1. Satu unit, satu token aktif.
    seen: dict[int, Token] = {}
    doubled = set()
    for t in open_tokens:
        if t.unit_id in seen:
            doubled.add(t.sku_id)
        seen[t.unit_id] = t
    results.append(Result("INV-1", "Satu keping paling banyak satu token aktif", not doubled, f"{len(doubled)} SKU bermasalah", doubled))

    # 2. Token aktif menunjuk tepat satu unit yang sedang dikunci.
    bad_link = {t.sku_id for t in open_tokens if t.unit_id not in units or units[t.unit_id].status not in LOCKED_UNIT or units[t.unit_id].sku_id != t.sku_id}
    results.append(Result("INV-2", "Token aktif menunjuk keping yang terkunci", not bad_link, f"{len(bad_link)} SKU bermasalah", bad_link))

    # 3. Unit AVAILABLE tidak terikat token aktif.
    avail_bound = {units[t.unit_id].sku_id for t in open_tokens if t.unit_id in units and units[t.unit_id].status == "AVAILABLE"}
    results.append(Result("INV-3", "Keping tersedia tidak punya token aktif", not avail_bound, f"{len(avail_bound)} SKU bermasalah", avail_bound))

    # 4. Jumlah token aktif = jumlah unit terkunci yang terikat token, per SKU.
    mismatched = []
    bound_ids = {t.unit_id for t in open_tokens}
    for sku in db.scalars(select(Sku)):
        token_count = sum(1 for t in open_tokens if t.sku_id == sku.id)
        locked = sum(1 for u in units.values() if u.sku_id == sku.id and u.status in LOCKED_UNIT and u.id in bound_ids)
        if token_count != locked:
            mismatched.append(sku.code)
    results.append(Result("INV-4", "Token aktif sama dengan keping terkunci per SKU", not mismatched, ", ".join(mismatched) or "Cocok"))

    # 5. Tanpa stok fiktif: setiap keping tersedia pernah lolos QC.
    qc_passed = set(db.scalars(select(UnitEvent.unit_id).where(UnitEvent.from_status == "INBOUND_QC", UnitEvent.to_status == "AVAILABLE")))
    fictive = [u.serial_number for u in units.values() if u.status == "AVAILABLE" and u.id not in qc_passed]
    results.append(Result("INV-5", "Keping tersedia sudah lolos QC", not fictive, f"{len(fictive)} keping"))

    # 6 & 7. Jejak audit utuh, rantai hash token, tidak ada perubahan setelah status final.
    broken, after_final = [], []
    events_by_token: dict = {}
    for e in db.scalars(select(TokenEvent).order_by(TokenEvent.id)):
        events_by_token.setdefault(e.token_id, []).append(e)
    for token in db.scalars(select(Token)):
        chain = events_by_token.get(token.id, [])
        prev = None
        for e in chain:
            expected = event_hash(prev, e.token_id, e.from_status, e.to_status, e.actor_type, e.actor_id, e.reason, e.created_at)
            if e.prev_hash != prev or e.hash != expected:
                broken.append(token.token_code)
                break
            if e.from_status in FINAL_STATUSES:
                after_final.append(token.token_code)
            prev = e.hash
        if not chain or chain[-1].to_status != token.status:
            broken.append(token.token_code)
    last_unit_event = dict(
        db.execute(
            select(UnitEvent.unit_id, UnitEvent.to_status).where(
                UnitEvent.id.in_(select(func.max(UnitEvent.id)).group_by(UnitEvent.unit_id))
            )
        ).all()
    )
    unit_gaps = [u.serial_number for u in units.values() if last_unit_event.get(u.id) != u.status]
    results.append(Result("INV-6", "Token final tidak pernah berubah lagi", not after_final, f"{len(after_final)} token"))
    results.append(
        Result("INV-7", "Setiap perubahan status tercatat", not broken and not unit_gaps, f"{len(set(broken))} token, {len(unit_gaps)} keping")
    )

    # 8. Setiap jurnal seimbang.
    unbalanced = db.scalars(
        select(LedgerEntry.journal_id).group_by(LedgerEntry.journal_id).having(func.sum(LedgerEntry.debit) != func.sum(LedgerEntry.credit))
    ).all()
    results.append(Result("INV-8", "Setiap jurnal ledger seimbang", not unbalanced, f"{len(unbalanced)} jurnal"))

    # 9. Saldo tidak minus, dan read-model cocok dengan mutasi.
    # available = semua entri kecuali SETTLE; held = dana yang ditahan dan belum dilepas atau dikeluarkan.
    expected: dict[int, list[int]] = {}
    for user_id, type_, amount in db.execute(
        select(WalletEntry.user_id, WalletEntry.type, func.sum(WalletEntry.amount)).group_by(WalletEntry.user_id, WalletEntry.type)
    ):
        avail_held = expected.setdefault(user_id, [0, 0])
        amount = int(amount)
        if type_ != "WITHDRAW_SETTLE":
            avail_held[0] += amount
        if type_ in ("WITHDRAW_HOLD", "WITHDRAW_RELEASE"):
            avail_held[1] -= amount
        elif type_ == "WITHDRAW_SETTLE":
            avail_held[1] += amount
    wallets = db.scalars(select(Wallet)).all()
    negative = [w.user_id for w in wallets if w.available < 0 or w.held < 0]
    drift = [w.user_id for w in wallets if [w.available, w.held] != expected.get(w.user_id, [0, 0])]
    results.append(Result("INV-9", "Saldo pengguna tidak minus dan cocok dengan mutasi", not negative and not drift, f"{len(negative)} minus, {len(drift)} tidak cocok"))

    # 10. Total saldo = kewajiban di ledger, dan tertutup rekening penampungan.
    wallet_total = sum(w.available + w.held for w in wallets)
    liability = ledger.balance(db, ledger.WALLET_LIABILITY)
    custody = ledger.balance(db, ledger.CUSTOMER_FUNDS)
    results.append(Result("INV-10", "Total saldo pengguna sama dengan kewajiban di ledger", wallet_total == liability, f"Selisih {wallet_total - liability}"))
    results.append(Result("INV-10B", "Saldo pengguna tertutup rekening dana pengguna", custody >= wallet_total, f"Kelebihan {custody - wallet_total}"))
    return results


def run(db: Session, actor=None) -> list[Result]:
    results = check_invariants(db)
    failed = [r for r in results if not r.ok]
    for r in failed:
        log.critical("Invarian dilanggar: %s %s (%s)", r.id, r.label, r.detail)
        emit(db, "alert.invariant_violation", invariant=r.id, detail=r.detail)
        audit(db, actor, "INVARIANT_VIOLATION", r.id, r.detail)

    halt_skus = set().union(*(r.sku_ids for r in failed if r.id in ("INV-1", "INV-2", "INV-3")))
    for sku in db.scalars(select(Sku).where(Sku.id.in_(halt_skus))):
        if not sku.trading_halted:
            sku.trading_halted, sku.halt_reason, sku.halted_by_admin_id = True, "Otomatis: pelanggaran invarian stok", None
            audit(db, None, "SKU_TRADING_HALTED", sku.code, sku.halt_reason)

    if any(r.id in ("INV-9", "INV-10", "INV-10B") for r in failed):
        item = flag(db, WITHDRAWALS_FROZEN)
        if not item.enabled:
            item.enabled, item.set_by_system, item.set_by_admin_id = True, True, None
            item.reason, item.updated_at = "Otomatis: pelanggaran invarian saldo", clock.now()
            audit(db, None, "WITHDRAWALS_FROZEN", "Penarikan", item.reason)
    return results
