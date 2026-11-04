"""State machine wajib sama dengan diagram di spesifikasi bagian 4."""

from sqlalchemy import select, text

from app.inventory.models import Unit, UnitEvent
from app.inventory.service import UNIT_TRANSITIONS
from app.reconciliation.service import check_invariants
from app.tokens.service import INITIAL, TRANSITIONS
from tests.conftest import PIN

# Ditulis ulang dari diagram, satu baris per panah.
TOKEN_DIAGRAM = {
    ("LOCKED_INSTALLMENT", "ACTIVE"),  # Cicilan lunas
    ("LOCKED_INSTALLMENT", "CANCELLED"),  # Wanprestasi / gagal bayar
    ("ACTIVE", "RETURNED"),  # Dijual kembali
    ("ACTIVE", "PRINT_PENDING"),  # Pengguna ajukan cetak fisik
    ("PRINT_PENDING", "ACTIVE"),  # Cetak dibatalkan (sebelum picking)
    ("PRINT_PENDING", "PRINTED"),  # Emas fisik diterima pengguna
}
TOKEN_START = {"ACTIVE", "LOCKED_INSTALLMENT"}

UNIT_DIAGRAM = {
    ("INBOUND_QC", "AVAILABLE"),  # Lolos QC
    ("INBOUND_QC", "QUARANTINE"),  # Gagal QC
    ("AVAILABLE", "RESERVED"),  # Order dibuat
    ("RESERVED", "AVAILABLE"),  # Waktu bayar habis
    ("RESERVED", "ALLOCATED"),  # Pembayaran lunas
    ("ALLOCATED", "AVAILABLE"),  # Token dijual kembali
    ("ALLOCATED", "PICKING"),  # Proses ambil barang untuk cetak
    ("PICKING", "ALLOCATED"),  # Cetak dibatalkan
    ("PICKING", "SHIPPED"),  # Dikirim kurir / ke cabang
    ("SHIPPED", "READY_PICKUP"),  # Tiba di cabang
    ("READY_PICKUP", "DELIVERED"),  # Diambil pengguna di cabang
    ("SHIPPED", "DELIVERED"),  # Diterima pengguna via kurir
    ("AVAILABLE", "QUARANTINE"),  # Temuan saat opname
    ("ALLOCATED", "QUARANTINE"),  # Temuan saat opname
    ("QUARANTINE", "AVAILABLE"),  # Masalah selesai & disetujui
    ("QUARANTINE", "WRITTEN_OFF"),  # Dihapusbukukan
}


def edges(transitions):
    return {(a, b) for a, targets in transitions.items() for b in targets}


def test_transisi_token_sama_dengan_diagram():
    assert edges(TRANSITIONS) == TOKEN_DIAGRAM
    assert INITIAL == TOKEN_START


def test_transisi_unit_sama_dengan_diagram():
    assert edges(UNIT_TRANSITIONS) == UNIT_DIAGRAM


def test_semua_event_unit_yang_tercatat_mengikuti_diagram(db, user, admin):
    """Jalankan alur yang menyentuh banyak transisi, lalu periksa setiap event di unit_events."""
    spv, staff = admin("spv"), admin("gudang-a")
    tokens = user.tokens()
    # Karantina keping bertoken (tukar), lalu lepas.
    unit = spv.ok("get", "/v1/admin/units", params={"q": tokens[0]["code"]})[0]
    spv.ok("post", f"/v1/admin/units/{unit['id']}/quarantine", {"reason": "Rusak"})
    spv.ok("post", f"/v1/admin/units/{unit['id']}/release")
    # Cetak lalu kirim JNE sampai selesai.
    order = user.ok("post", "/v1/print-orders", {"token_codes": [tokens[1]["code"]], "method": "SHIP", "address": "Jl. A", "pin": PIN})
    staff.ok("post", f"/v1/admin/print-orders/{order['id']}/pick", {"serial_number": order["items"][0]["serial_number"]})
    staff.ok("post", f"/v1/admin/print-orders/{order['id']}/ship", {"tracking_number": "JNE1"})
    staff.ok("post", f"/v1/admin/print-orders/{order['id']}/complete")

    db.expire_all()
    for e in db.scalars(select(UnitEvent).where(UnitEvent.from_status.is_not(None))):
        assert (e.from_status, e.to_status) in UNIT_DIAGRAM, (e.from_status, e.to_status, e.reason)
    assert [r.id for r in check_invariants(db) if not r.ok] == []


def test_keping_pengganti_lewat_reserved(db, user, admin):
    spv = admin("spv")
    token = user.tokens()[0]
    old = spv.ok("get", "/v1/admin/units", params={"q": token["code"]})[0]
    result = spv.ok("post", f"/v1/admin/units/{old['id']}/quarantine", {"reason": "Goresan"})
    new = db.scalar(select(Unit).where(Unit.serial_number == result["replacement_serial"]))
    steps = db.execute(
        select(UnitEvent.from_status, UnitEvent.to_status).where(UnitEvent.unit_id == new.id).order_by(UnitEvent.id)
    ).all()
    assert [tuple(s) for s in steps[-2:]] == [("AVAILABLE", "RESERVED"), ("RESERVED", "ALLOCATED")]


def test_lepas_karantina_keping_yang_masih_bertoken(db, user, admin):
    """Tukar gagal (tidak ada stok pengganti): token tetap di keping karantina, lalu diikat kembali saat dilepas."""
    spv = admin("spv")
    token = next(t for t in user.tokens() if t["sku_code"] == "LMRE-0.5G")
    unit = spv.ok("get", "/v1/admin/units", params={"q": token["code"]})[0]
    db.execute(text("UPDATE units SET status='WRITTEN_OFF' WHERE sku_id=:s AND status='AVAILABLE'"), {"s": unit["sku_id"]})
    db.commit()

    result = spv.ok("post", f"/v1/admin/units/{unit['id']}/quarantine", {"reason": "Rusak"})
    assert result["replacement_serial"] is None and result["unit"]["token_code"] == token["code"]
    released = spv.ok("post", f"/v1/admin/units/{unit['id']}/release")
    assert released["status"] == "ALLOCATED" and released["token_code"] == token["code"]
    steps = db.execute(
        select(UnitEvent.from_status, UnitEvent.to_status).where(UnitEvent.unit_id == unit["id"]).order_by(UnitEvent.id)
    ).all()
    assert [tuple(s) for s in steps[-3:]] == [("QUARANTINE", "AVAILABLE"), ("AVAILABLE", "RESERVED"), ("RESERVED", "ALLOCATED")]


def test_pengguna_tidak_bisa_batal_cetak_setelah_picking_dimulai(user, admin):
    staff = admin("gudang-a")
    codes = [t["code"] for t in user.tokens() if t["sku_code"] == "LMRE-1G"]
    order = user.ok("post", "/v1/print-orders", {"token_codes": codes, "method": "SHIP", "address": "Jl. A", "pin": PIN})
    staff.ok("post", f"/v1/admin/print-orders/{order['id']}/pick", {"serial_number": order["items"][0]["serial_number"]})
    assert user.ok("get", f"/v1/print-orders/{order['id']}")["status"] == "WAITING"  # baru sebagian
    r = user.post(f"/v1/print-orders/{order['id']}/cancel")
    assert r.json()["error"]["code"] == "INVALID_STATE"
