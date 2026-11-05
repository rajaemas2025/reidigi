"""Keping fisik: penerimaan, QC, karantina (dengan tukar keping), hapus buku, dan stock opname."""

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.admin.audit import actor_ref, audit
from app.catalog.models import Sku
from app.core.config import get_settings
from app.core.errors import ApiError
from app.inventory.models import (
    InboundBatch,
    Location,
    StockCount,
    StockCountScan,
    StockCountVariance,
    TokenUnitHistory,
    Unit,
    UnitEvent,
)
from app.tokens.models import FINAL_STATUSES, Token

# Sama persis dengan diagram "Siklus Hidup Unit Fisik" di spesifikasi bagian 4.
UNIT_TRANSITIONS: dict[str, set[str]] = {
    "INBOUND_QC": {"AVAILABLE", "QUARANTINE"},
    "AVAILABLE": {"RESERVED", "QUARANTINE"},
    "RESERVED": {"AVAILABLE", "ALLOCATED"},
    "ALLOCATED": {"AVAILABLE", "PICKING", "QUARANTINE"},
    "PICKING": {"ALLOCATED", "SHIPPED"},
    "SHIPPED": {"READY_PICKUP", "DELIVERED"},
    "READY_PICKUP": {"DELIVERED"},
    "QUARANTINE": {"AVAILABLE", "WRITTEN_OFF"},
    "DELIVERED": set(),
    "WRITTEN_OFF": set(),
}
# Keping yang masih secara fisik ada di lokasinya.
PHYSICALLY_PRESENT = ("AVAILABLE", "RESERVED", "ALLOCATED", "PICKING", "READY_PICKUP", "QUARANTINE", "INBOUND_QC")
LOCKED_UNIT = ("ALLOCATED", "PICKING", "SHIPPED", "READY_PICKUP", "QUARANTINE")


def set_status(db: Session, unit: Unit, to_status: str, actor_type: str, actor_id=None, reason: str | None = None):
    if to_status not in UNIT_TRANSITIONS[unit.status]:
        raise ApiError("INVALID_STATE", f"Keping {unit.serial_number} tidak bisa berubah dari {unit.status} ke {to_status}.")
    db.add(
        UnitEvent(
            unit_id=unit.id,
            from_status=unit.status,
            to_status=to_status,
            actor_type=actor_type,
            actor_id=str(actor_id) if actor_id else None,
            reason=reason,
        )
    )
    unit.status = to_status


def reserve_units(db: Session, sku_id: int, quantity: int, actor_type: str, actor_id, reason: str) -> list[Unit]:
    """FOR UPDATE SKIP LOCKED: transaksi lain mengambil keping berikutnya, tidak mengantre."""
    units = db.scalars(
        select(Unit)
        .where(Unit.sku_id == sku_id, Unit.status == "AVAILABLE")
        .order_by(Unit.id)
        .limit(quantity)
        .with_for_update(skip_locked=True)
    ).all()
    if len(units) < quantity:
        raise ApiError("INSUFFICIENT_STOCK", "Stok tidak mencukupi.")
    for unit in units:
        set_status(db, unit, "RESERVED", actor_type, actor_id, reason)
    return list(units)


def lock_units(db: Session, unit_ids: list[int]) -> dict[int, Unit]:
    units = db.scalars(select(Unit).where(Unit.id.in_(unit_ids)).order_by(Unit.id).with_for_update()).all()
    return {u.id: u for u in units}


def available_count(db: Session, sku_id: int) -> int:
    return db.scalar(select(func.count()).where(Unit.sku_id == sku_id, Unit.status == "AVAILABLE")) or 0


def open_token_for_unit(db: Session, unit_id: int) -> Token | None:
    return db.scalar(select(Token).where(Token.unit_id == unit_id, Token.status.not_in(FINAL_STATUSES)))


def _unit(db: Session, unit_id: int) -> Unit:
    unit = db.get(Unit, unit_id, with_for_update=True)
    if unit is None:
        raise ApiError("NOT_FOUND", "Keping tidak ditemukan.")
    return unit


# ----- Penerimaan dan QC -----


def receive(db: Session, actor, sku_id: int, location_id: int, serials: list[str], note: str | None = None) -> InboundBatch:
    serials = list(dict.fromkeys(s.strip().upper() for s in serials if s.strip()))
    if not serials:
        raise ApiError("INVALID", "Masukkan minimal satu nomor seri.")
    sku = db.get(Sku, sku_id)
    if sku is None:
        raise ApiError("NOT_FOUND", "SKU tidak ditemukan.")
    if db.get(Location, location_id) is None:
        raise ApiError("NOT_FOUND", "Lokasi tidak ditemukan.")
    taken = db.scalars(select(Unit.serial_number).where(Unit.sku_id == sku_id, Unit.serial_number.in_(serials))).all()
    if taken:
        raise ApiError("DUPLICATE", f"Nomor seri sudah terdaftar: {', '.join(taken)}")
    batch = InboundBatch(sku_id=sku_id, location_id=location_id, quantity=len(serials), note=note, received_by_admin_id=actor.id)
    db.add(batch)
    db.flush()
    actor_type, actor_id = actor_ref(actor)
    for serial in serials:
        unit = Unit(sku_id=sku_id, serial_number=serial, location_id=location_id, status="INBOUND_QC", inbound_batch_id=batch.id)
        db.add(unit)
        db.flush()
        db.add(UnitEvent(unit_id=unit.id, from_status=None, to_status="INBOUND_QC", actor_type=actor_type, actor_id=actor_id, reason=f"Penerimaan #{batch.id}"))
    audit(db, actor, "UNITS_RECEIVED", sku.code, f"{len(serials)} keping masuk QC")
    return batch


def decide_qc(db: Session, actor, unit_id: int, passed: bool, note: str | None = None) -> Unit:
    """Invarian #5: keping baru tersedia setelah diterima gudang dan lolos QC."""
    unit = _unit(db, unit_id)
    if unit.status != "INBOUND_QC":
        raise ApiError("INVALID_STATE", "Keping tidak sedang menunggu QC.")
    actor_type, actor_id = actor_ref(actor)
    set_status(db, unit, "AVAILABLE" if passed else "QUARANTINE", actor_type, actor_id, note or ("Lolos QC" if passed else "Gagal QC"))
    audit(db, actor, "QC_PASSED" if passed else "QC_FAILED", unit.serial_number, note or "")
    return unit


# ----- Karantina -----


def quarantine(db: Session, actor, unit_id: int, reason: str) -> Unit | None:
    if not reason.strip():
        raise ApiError("INVALID", "Alasan wajib diisi.")
    unit = _unit(db, unit_id)
    if unit.status not in ("AVAILABLE", "ALLOCATED"):
        raise ApiError("INVALID_STATE", "Keping dengan status ini tidak bisa dikarantina.")
    return _quarantine_unit(db, actor, unit, reason.strip())


def _quarantine_unit(db: Session, actor, unit: Unit, reason: str) -> Unit | None:
    """Masalah gudang tidak boleh merugikan pengguna: token pindah ke keping lain dari SKU yang sama bila ada."""
    from app.fulfillment.models import PrintOrder, PrintOrderItem

    actor_type, actor_id = actor_ref(actor)
    set_status(db, unit, "QUARANTINE", actor_type, actor_id, reason)
    audit(db, actor, "UNIT_QUARANTINED", unit.serial_number, reason)
    token = open_token_for_unit(db, unit.id)
    if token is None:
        return None
    replacement = db.scalar(
        select(Unit)
        .where(Unit.sku_id == unit.sku_id, Unit.status == "AVAILABLE", Unit.id != unit.id)
        .order_by(Unit.location_id, Unit.id)
        .limit(1)
        .with_for_update(skip_locked=True)
    )
    if replacement is None:
        audit(db, actor, "TOKEN_SWAP_FAILED", token.token_code, f"Tidak ada keping pengganti untuk {unit.serial_number}")
        return None
    _bind_available_unit(db, replacement, actor_type, actor_id, f"Pengganti {unit.serial_number} untuk {token.token_code}")
    token.unit_id = replacement.id
    db.add(TokenUnitHistory(token_id=token.id, from_unit_id=unit.id, to_unit_id=replacement.id, reason=reason, actor_type=actor_type, actor_id=actor_id))
    # Pesanan cetak yang belum diambil ikut menunjuk keping pengganti.
    for item in db.scalars(
        select(PrintOrderItem)
        .join(PrintOrder, PrintOrder.id == PrintOrderItem.print_order_id)
        .where(PrintOrderItem.token_id == token.id, PrintOrder.status == "WAITING")
    ):
        item.unit_id = replacement.id
    audit(db, actor, "TOKEN_SWAPPED", token.token_code, f"{unit.serial_number} → {replacement.serial_number}")
    return replacement


def _bind_available_unit(db: Session, unit: Unit, actor_type: str, actor_id, reason: str) -> None:
    """Keping AVAILABLE diikat ke token lewat jalur diagram: AVAILABLE → RESERVED → ALLOCATED, dalam satu transaksi."""
    set_status(db, unit, "RESERVED", actor_type, actor_id, reason)
    set_status(db, unit, "ALLOCATED", actor_type, actor_id, reason)


def release_quarantine(db: Session, actor, unit_id: int, note: str | None = None) -> Unit:
    unit = _unit(db, unit_id)
    if unit.status != "QUARANTINE":
        raise ApiError("INVALID_STATE", "Keping tidak sedang dikarantina.")
    actor_type, actor_id = actor_ref(actor)
    set_status(db, unit, "AVAILABLE", actor_type, actor_id, note or "Masalah selesai dan disetujui")
    token = open_token_for_unit(db, unit.id)
    if token:
        # Tukar keping sebelumnya gagal, jadi token masih menunjuk keping ini: ikat kembali.
        _bind_available_unit(db, unit, actor_type, actor_id, f"Kembali ke token {token.token_code}")
    audit(db, actor, "UNIT_RELEASED", unit.serial_number, note or "")
    return unit


def write_off(db: Session, actor, unit_id: int, reason: str) -> Unit:
    if not reason.strip():
        raise ApiError("INVALID", "Alasan wajib diisi.")
    unit = _unit(db, unit_id)
    if unit.status != "QUARANTINE":
        raise ApiError("INVALID_STATE", "Hanya keping karantina yang bisa dihapusbukukan.")
    if open_token_for_unit(db, unit.id):
        raise ApiError("INVALID_STATE", "Keping masih terikat token. Pindahkan token ke keping lain dulu.")
    actor_type, actor_id = actor_ref(actor)
    set_status(db, unit, "WRITTEN_OFF", actor_type, actor_id, reason)
    audit(db, actor, "UNIT_WRITTEN_OFF", unit.serial_number, reason)
    return unit


# ----- Stock opname -----


def _count(db: Session, count_id: int) -> StockCount:
    count = db.get(StockCount, count_id, with_for_update=True)
    if count is None:
        raise ApiError("NOT_FOUND", "Sesi opname tidak ditemukan.")
    return count


def create_count(db: Session, actor, location_id: int) -> StockCount:
    if db.get(Location, location_id) is None:
        raise ApiError("NOT_FOUND", "Lokasi tidak ditemukan.")
    busy = db.scalar(select(StockCount.id).where(StockCount.location_id == location_id, StockCount.status != "APPROVED"))
    if busy:
        raise ApiError("COUNT_IN_PROGRESS", "Lokasi ini masih punya sesi opname yang belum selesai.")
    expected = db.scalars(
        select(Unit.id).where(Unit.location_id == location_id, Unit.status.in_(PHYSICALLY_PRESENT)).order_by(Unit.id)
    ).all()
    count = StockCount(location_id=location_id, status="COUNTING", expected_unit_ids=",".join(map(str, expected)), created_by_admin_id=actor.id)
    db.add(count)
    db.flush()
    audit(db, actor, "COUNT_CREATED", f"Opname #{count.id}", f"Lokasi {location_id}")
    return count


def scan(db: Session, actor, count_id: int, raw_serial: str) -> tuple[bool, int]:
    count = _count(db, count_id)
    if count.status != "COUNTING":
        raise ApiError("INVALID_STATE", "Sesi ini tidak lagi menerima pindaian.")
    serial = raw_serial.strip().upper()
    if not serial:
        raise ApiError("INVALID", "Nomor seri kosong.")
    exists = db.scalar(select(StockCountScan.id).where(StockCountScan.count_id == count_id, StockCountScan.serial_number == serial))
    if not exists:
        db.add(StockCountScan(count_id=count_id, serial_number=serial, scanned_by_admin_id=actor.id))
        db.flush()
    total = db.scalar(select(func.count()).where(StockCountScan.count_id == count_id))
    return bool(exists), total


def submit_count(db: Session, actor, count_id: int) -> StockCount:
    count = _count(db, count_id)
    if count.status != "COUNTING":
        raise ApiError("INVALID_STATE", "Sesi ini sudah dikirim.")
    scanned = set(db.scalars(select(StockCountScan.serial_number).where(StockCountScan.count_id == count_id)))
    if not scanned:
        raise ApiError("INVALID", "Belum ada pindaian.")
    expected = set(count.expected_ids)
    variances: list[StockCountVariance] = []
    for unit in db.scalars(select(Unit).where(Unit.id.in_(expected))):
        # Keping yang keluar secara sah selama sesi bukan selisih.
        moved_away = unit.location_id != count.location_id or unit.status not in PHYSICALLY_PRESENT
        if not moved_away and unit.serial_number not in scanned:
            variances.append(StockCountVariance(count_id=count_id, type="MISSING", serial_number=unit.serial_number, unit_id=unit.id))
    for serial in scanned:
        unit = db.scalar(select(Unit).where(Unit.serial_number == serial).limit(1))
        if unit is None:
            variances.append(StockCountVariance(count_id=count_id, type="UNEXPECTED", serial_number=serial))
        elif (unit.location_id != count.location_id or unit.status not in PHYSICALLY_PRESENT) and unit.id not in expected:
            variances.append(StockCountVariance(count_id=count_id, type="WRONG_LOCATION", serial_number=serial, unit_id=unit.id))
    db.add_all(variances)
    count.status = "RECOUNT" if variances else "AWAITING_APPROVAL"
    audit(db, actor, "COUNT_SUBMITTED", f"Opname #{count_id}", f"{len(scanned)} pindaian, {len(variances)} selisih")
    db.flush()
    return count


def record_recount(db: Session, actor, count_id: int, variance_id: int, found: bool) -> StockCount:
    count = _count(db, count_id)
    if count.status != "RECOUNT":
        raise ApiError("INVALID_STATE", "Sesi ini tidak sedang hitung ulang.")
    counted_first = db.scalar(
        select(StockCountScan.id).where(StockCountScan.count_id == count_id, StockCountScan.scanned_by_admin_id == actor.id).limit(1)
    )
    if counted_first:
        raise ApiError("SAME_COUNTER", "Hitung ulang harus dilakukan orang yang berbeda dari penghitung pertama.")
    variance = db.get(StockCountVariance, variance_id)
    if variance is None or variance.count_id != count_id:
        raise ApiError("NOT_FOUND", "Selisih tidak ditemukan.")
    variance.recount_found = found
    variance.recount_by_admin_id = actor.id
    audit(db, actor, "COUNT_RECOUNTED", variance.serial_number, "Ditemukan" if found else "Tidak ditemukan")
    db.flush()
    pending = db.scalar(select(func.count()).where(StockCountVariance.count_id == count_id, StockCountVariance.recount_found.is_(None)))
    if not pending:
        count.status = "AWAITING_APPROVAL"
    return count


def _missing_weight(db: Session, count_id: int) -> int:
    return db.scalar(
        select(func.coalesce(func.sum(Sku.weight_mg), 0))
        .select_from(StockCountVariance)
        .join(Unit, Unit.id == StockCountVariance.unit_id)
        .join(Sku, Sku.id == Unit.sku_id)
        .where(StockCountVariance.count_id == count_id, StockCountVariance.type == "MISSING", StockCountVariance.recount_found.is_(False))
    )


def approve_count(db: Session, actor, count_id: int) -> StockCount:
    """Stok hanya berubah di sini, lewat penyesuaian yang disetujui."""
    count = _count(db, count_id)
    if count.status == "AWAITING_APPROVAL":
        if actor.role != "WAREHOUSE_SUPERVISOR":
            raise ApiError("FORBIDDEN", "Peran Anda tidak berwenang melakukan aksi ini.")
        if _missing_weight(db, count_id) >= get_settings().finance_approval_mg:
            count.status = "AWAITING_FINANCE"
            audit(db, actor, "COUNT_ESCALATED", f"Opname #{count_id}", "Selisih besar, menunggu Finance")
            return count
    elif count.status == "AWAITING_FINANCE":
        if actor.role != "FINANCE":
            raise ApiError("FORBIDDEN", "Selisih besar harus disetujui Finance.")
    else:
        raise ApiError("INVALID_STATE", "Sesi ini belum siap disetujui.")

    variances = db.scalars(select(StockCountVariance).where(StockCountVariance.count_id == count_id)).all()
    for v in variances:
        if v.type == "MISSING" and v.recount_found is False:
            unit = _unit(db, v.unit_id)
            if unit.status in ("AVAILABLE", "ALLOCATED"):
                _quarantine_unit(db, actor, unit, f"Hilang saat opname #{count_id}")
        elif v.type == "WRONG_LOCATION" and v.recount_found:
            unit = _unit(db, v.unit_id)
            unit.location_id = count.location_id
            audit(db, actor, "UNIT_RELOCATED", v.serial_number, f"Dikoreksi oleh opname #{count_id}")
        elif v.type == "UNEXPECTED" and v.recount_found:
            audit(db, actor, "UNEXPECTED_UNIT", v.serial_number, "Perlu didaftarkan lewat penerimaan barang")
    count.status = "APPROVED"
    count.approved_by_admin_id = actor.id
    audit(db, actor, "COUNT_APPROVED", f"Opname #{count_id}", f"{len(variances)} selisih diproses")
    return count
