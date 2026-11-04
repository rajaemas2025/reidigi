"""Cetak fisik: kirim JNE atau ambil di cabang."""

import secrets
from datetime import timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.admin.audit import actor_ref, audit
from app.catalog.models import Sku
from app.core import clock
from app.core.config import get_settings
from app.core.errors import ApiError
from app.core.models import emit
from app.fulfillment.models import PrintOrder, PrintOrderItem, Shipment
from app.identity.service import require_verified, verify_pin
from app.inventory import service as inventory
from app.inventory.models import Location, Unit
from app.tokens import service as tokens
from app.tokens.models import Token


def items(db: Session, order_id: int) -> list[PrintOrderItem]:
    return list(db.scalars(select(PrintOrderItem).where(PrintOrderItem.print_order_id == order_id).order_by(PrintOrderItem.id)))


def _order(db: Session, order_id: int) -> PrintOrder:
    order = db.get(PrintOrder, order_id, with_for_update=True)
    if order is None:
        raise ApiError("NOT_FOUND", "Pesanan cetak tidak ditemukan.")
    return order


def create(db: Session, user, token_codes: list[str], method: str, address: str | None, branch_code: str | None, pin: str | None) -> PrintOrder:
    verify_pin(db, user, pin)
    require_verified(user)
    codes = list(dict.fromkeys(c.strip().upper() for c in token_codes))
    if not codes:
        raise ApiError("INVALID", "Pilih minimal satu token.")
    unit_ids = db.scalars(select(Token.unit_id).where(Token.token_code.in_(codes), Token.user_id == user.id)).all()
    units = inventory.lock_units(db, list(unit_ids))
    owned = tokens.lock_user_tokens(db, user.id, codes)
    if any(t.status != "ACTIVE" or units[t.unit_id].status != "ALLOCATED" for t in owned):
        raise ApiError("TOKEN_LOCKED", "Ada token yang tidak bisa dicetak saat ini.")

    branch = None
    if method == "PICKUP":
        total_mg = sum(db.get(Sku, t.sku_id).weight_mg for t in owned)
        if total_mg < get_settings().pickup_min_weight_mg:
            raise ApiError("PICKUP_MIN_WEIGHT_NOT_MET", "Ambil di cabang memerlukan total minimal 2 gr. Pilih pengiriman JNE atau tambah token.")
        branch = db.scalar(select(Location).where(Location.code == (branch_code or "").lower(), Location.type == "BRANCH"))
        if branch is None:
            raise ApiError("INVALID", "Pilih cabang pengambilan.")
    elif method == "SHIP":
        if not (address or "").strip():
            raise ApiError("INVALID", "Alamat pengiriman wajib diisi.")
    else:
        raise ApiError("INVALID", "Metode cetak tidak dikenal.")

    order = PrintOrder(
        user_id=user.id, method=method, address=(address or "").strip() or None,
        branch_location_id=branch.id if branch else None, status="WAITING",
    )
    db.add(order)
    db.flush()
    for t in owned:
        tokens.transition(db, t, "PRINT_PENDING", "USER", user.id, f"Cetak #{order.id}")
        db.add(PrintOrderItem(print_order_id=order.id, token_id=t.id, unit_id=t.unit_id))
    audit(db, user, "PRINT_REQUESTED", f"Cetak #{order.id}", f"{method} {branch.name if branch else ''}".strip())
    emit(db, "print.requested", print_order_id=order.id)
    db.flush()
    return order


def _restore(db: Session, order: PrintOrder, actor, reason: str) -> None:
    actor_type, actor_id = actor_ref(actor)
    for item in items(db, order.id):
        unit = db.get(Unit, item.unit_id, with_for_update=True)
        if unit.status == "PICKING":
            inventory.set_status(db, unit, "ALLOCATED", actor_type, actor_id, reason)
    for item in items(db, order.id):
        token = db.get(Token, item.token_id, with_for_update=True)
        if token.status == "PRINT_PENDING":
            tokens.transition(db, token, "ACTIVE", actor_type, actor_id, reason)
    order.status = "CANCELLED"
    order.updated_at = clock.now()


def cancel_by_user(db: Session, user, order_id: int) -> PrintOrder:
    order = _order(db, order_id)
    if order.user_id != user.id:
        raise ApiError("NOT_FOUND", "Pesanan cetak tidak ditemukan.")
    # Diagram token: PRINT_PENDING → ACTIVE hanya sebelum picking. Satu keping terpindai pun sudah dihitung picking.
    if order.status != "WAITING" or any(i.picked for i in items(db, order.id)):
        raise ApiError("INVALID_STATE", "Pesanan sudah diproses gudang dan tidak bisa dibatalkan.")
    _restore(db, order, user, f"Cetak #{order.id} dibatalkan pengguna")
    audit(db, user, "PRINT_CANCELLED", f"Cetak #{order.id}")
    return order


def cancel_by_admin(db: Session, actor, order_id: int, reason: str) -> PrintOrder:
    if not reason.strip():
        raise ApiError("INVALID", "Alasan wajib diisi.")
    order = _order(db, order_id)
    if order.status not in ("WAITING", "PICKED"):
        raise ApiError("INVALID_STATE", "Pesanan yang sudah dikirim tidak bisa dibatalkan.")
    _restore(db, order, actor, reason.strip())
    audit(db, actor, "PRINT_CANCELLED", f"Cetak #{order.id}", reason.strip())
    return order


def pick(db: Session, actor, order_id: int, serial: str) -> PrintOrder:
    order = _order(db, order_id)
    if order.status != "WAITING":
        raise ApiError("INVALID_STATE", "Pesanan ini sudah diproses.")
    serial = serial.strip().upper()
    order_items = items(db, order.id)
    match = next((i for i in order_items if db.get(Unit, i.unit_id).serial_number == serial), None)
    if match is None:
        raise ApiError("SERIAL_MISMATCH", "Nomor seri tidak cocok dengan keping milik token.")
    if match.picked:
        raise ApiError("DUPLICATE", "Keping ini sudah dipindai.")
    actor_type, actor_id = actor_ref(actor)
    inventory.set_status(db, db.get(Unit, match.unit_id, with_for_update=True), "PICKING", actor_type, actor_id, f"Cetak #{order.id}")
    match.picked = True
    if all(i.picked for i in order_items):
        order.status = "PICKED"
    order.updated_at = clock.now()
    audit(db, actor, "PRINT_PICKED", serial, f"Cetak #{order.id}")
    return order


def ship(db: Session, actor, order_id: int, tracking_number: str, carrier: str = "JNE") -> PrintOrder:
    """JNE ke pengguna, atau pengiriman internal gudang ke cabang."""
    order = _order(db, order_id)
    if order.status != "PICKED":
        raise ApiError("INVALID_STATE", "Pesanan belum bisa dikirim.")
    if not tracking_number.strip():
        raise ApiError("INVALID", "Nomor pengiriman wajib diisi.")
    actor_type, actor_id = actor_ref(actor)
    for item in items(db, order.id):
        inventory.set_status(db, db.get(Unit, item.unit_id, with_for_update=True), "SHIPPED", actor_type, actor_id, tracking_number.strip())
    kind = "TO_BRANCH" if order.method == "PICKUP" else "TO_CUSTOMER"
    db.add(Shipment(print_order_id=order.id, kind=kind, carrier=carrier, tracking_number=tracking_number.strip()))
    order.status = "IN_TRANSIT" if order.method == "PICKUP" else "SHIPPED"
    order.updated_at = clock.now()
    audit(db, actor, "PRINT_SHIPPED", f"Cetak #{order.id}", tracking_number.strip())
    emit(db, "print.shipped", print_order_id=order.id)
    return order


def receive_at_branch(db: Session, actor, order_id: int, serial: str) -> PrintOrder:
    order = _order(db, order_id)
    if order.status != "IN_TRANSIT":
        raise ApiError("INVALID_STATE", "Pesanan tidak sedang dikirim ke cabang.")
    serial = serial.strip().upper()
    order_items = items(db, order.id)
    match = next((i for i in order_items if db.get(Unit, i.unit_id).serial_number == serial), None)
    if match is None:
        raise ApiError("SERIAL_MISMATCH", "Nomor seri tidak termasuk pesanan ini.")
    if match.arrived:
        raise ApiError("DUPLICATE", "Keping ini sudah dipindai.")
    unit = db.get(Unit, match.unit_id, with_for_update=True)
    actor_type, actor_id = actor_ref(actor)
    inventory.set_status(db, unit, "READY_PICKUP", actor_type, actor_id, f"Tiba di cabang, cetak #{order.id}")
    unit.location_id = order.branch_location_id
    match.arrived = True
    if all(i.arrived for i in order_items):
        order.status = "READY_PICKUP"
        order.pickup_code = "".join(secrets.choice("0123456789") for _ in range(6))
        order.pickup_deadline = clock.now() + timedelta(days=get_settings().pickup_deadline_days)
        emit(db, "print.ready_pickup", print_order_id=order.id)
    order.updated_at = clock.now()
    audit(db, actor, "PRINT_ARRIVED_AT_BRANCH", serial, f"Cetak #{order.id}")
    return order


def _finish(db: Session, actor, order: PrintOrder) -> None:
    actor_type, actor_id = actor_ref(actor)
    for item in items(db, order.id):
        inventory.set_status(db, db.get(Unit, item.unit_id, with_for_update=True), "DELIVERED", actor_type, actor_id, f"Cetak #{order.id} selesai")
    for item in items(db, order.id):
        tokens.transition(db, db.get(Token, item.token_id, with_for_update=True), "PRINTED", actor_type, actor_id, f"Cetak #{order.id} diterima")
    order.status = "DONE"
    order.updated_at = clock.now()
    audit(db, actor, "PRINT_DONE", f"Cetak #{order.id}")
    emit(db, "print.done", print_order_id=order.id)


def complete_delivery(db: Session, actor, order_id: int) -> PrintOrder:
    order = _order(db, order_id)
    if order.status != "SHIPPED":
        raise ApiError("INVALID_STATE", "Pesanan belum bisa diselesaikan.")
    _finish(db, actor, order)
    return order


def complete_pickup(db: Session, actor, order_id: int, code: str, identity_checked: bool) -> PrintOrder:
    order = _order(db, order_id)
    if order.status != "READY_PICKUP":
        raise ApiError("INVALID_STATE", "Pesanan belum siap diambil.")
    if not identity_checked:
        raise ApiError("INVALID", "Periksa KTP pengguna terlebih dahulu.")
    if not secrets.compare_digest(code.strip(), order.pickup_code or ""):
        raise ApiError("INVALID_PICKUP_CODE", "Kode pengambilan salah.")
    _finish(db, actor, order)
    return order
