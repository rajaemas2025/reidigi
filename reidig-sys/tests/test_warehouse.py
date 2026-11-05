"""Gudang, opname, karantina, dan cetak fisik."""

from sqlalchemy import select

from app.inventory.models import TokenUnitHistory, Unit
from app.reconciliation.service import check_invariants
from tests.conftest import PIN


def clean(db):
    db.expire_all()
    return [r.id for r in check_invariants(db) if not r.ok] == []


def unit_of(spv, token_code):
    return spv.ok("get", "/v1/admin/units", params={"q": token_code})[0]


def test_barang_masuk_baru_tersedia_setelah_lolos_qc(db, admin):
    spv = admin("spv")
    sku = next(s for s in spv.ok("get", "/v1/admin/skus") if s["code"] == "LMWD-1G")
    spv.ok("post", "/v1/admin/units/receive", {"sku_id": sku["id"], "location_id": 1, "serials": ["wd-1", "WD-2", "wd-1"]})
    units = spv.ok("get", "/v1/admin/units", params={"sku_id": sku["id"]})
    assert [u["status"] for u in units] == ["INBOUND_QC", "INBOUND_QC"]
    assert next(s for s in spv.ok("get", "/v1/admin/skus") if s["code"] == "LMWD-1G")["available"] == 0
    spv.ok("post", f"/v1/admin/units/{units[0]['id']}/qc", {"passed": True})
    spv.ok("post", f"/v1/admin/units/{units[1]['id']}/qc", {"passed": False, "note": "Kemasan rusak"})
    assert next(s for s in spv.ok("get", "/v1/admin/skus") if s["code"] == "LMWD-1G")["available"] == 1
    assert clean(db)


def test_karantina_keping_bertoken_menukar_ke_keping_lain(db, user, admin):
    spv = admin("spv")
    token = user.tokens()[0]
    old = unit_of(spv, token["code"])
    result = spv.ok("post", f"/v1/admin/units/{old['id']}/quarantine", {"reason": "Goresan saat opname"})
    assert result["replacement_serial"] and result["unit"]["token_code"] is None
    assert user.ok("get", f"/v1/tokens/{token['code']}")["serial_number"] == result["replacement_serial"]
    assert db.scalar(select(TokenUnitHistory)) is not None
    assert clean(db)


def test_opname_buta_hitung_ulang_beda_orang_dan_keping_hilang_dikarantina(db, user, admin):
    spv, a, b = admin("spv"), admin("gudang-a"), admin("gudang-b")
    token = user.tokens()[0]
    missing = unit_of(spv, token["code"])
    count = spv.ok("post", "/v1/admin/counts", {"location_id": 1})
    assert count["expected_unit_ids"] == []  # hitung buta

    present = [u for u in spv.ok("get", "/v1/admin/units", params={"location_id": 1, "limit": 2000}) if u["status"] not in ("SHIPPED", "DELIVERED")]
    for u in present:
        if u["id"] != missing["id"]:
            a.ok("post", f"/v1/admin/counts/{count['id']}/scans", {"serial_number": u["serial_number"]})
    dup = a.ok("post", f"/v1/admin/counts/{count['id']}/scans", {"serial_number": f" {present[1]['serial_number'].lower()} "})
    assert dup["duplicate"]

    submitted = a.ok("post", f"/v1/admin/counts/{count['id']}/submit")
    assert submitted["status"] == "RECOUNT"
    [variance] = submitted["variances"]
    assert variance["type"] == "MISSING" and variance["unit_id"] == missing["id"]
    assert db.get(Unit, missing["id"]).status == "ALLOCATED"  # stok belum berubah sebelum disetujui

    r = a.post(f"/v1/admin/counts/{count['id']}/variances/{variance['id']}/recount", {"found": False})
    assert r.json()["error"]["code"] == "SAME_COUNTER"
    b.ok("post", f"/v1/admin/counts/{count['id']}/variances/{variance['id']}/recount", {"found": False})
    approved = spv.ok("post", f"/v1/admin/counts/{count['id']}/approve")
    assert approved["status"] == "APPROVED"

    db.expire_all()
    assert db.get(Unit, missing["id"]).status == "QUARANTINE"
    assert user.ok("get", f"/v1/tokens/{token['code']}")["serial_number"] != missing["serial_number"]
    assert clean(db)


def test_cetak_ambil_di_cabang_dari_awal_sampai_selesai(db, user, admin):
    staff = admin("gudang-a")
    codes = [t["code"] for t in user.tokens() if t["sku_code"] == "LMRE-1G"]
    assert len(codes) == 2  # 2 gr, batas minimal ambil di cabang

    small = [t["code"] for t in user.tokens() if t["sku_code"] == "LMRE-0.5G"]
    r = user.post("/v1/print-orders", {"token_codes": small, "method": "PICKUP", "branch_code": "ptj", "pin": PIN})
    assert r.json()["error"]["code"] == "PICKUP_MIN_WEIGHT_NOT_MET"

    order = user.ok("post", "/v1/print-orders", {"token_codes": codes, "method": "PICKUP", "branch_code": "ptj", "pin": PIN})
    assert order["status"] == "WAITING"
    assert {t["status"] for t in user.tokens() if t["code"] in codes} == {"PRINT_PENDING"}
    q = user.post("/v1/quotes", {"sku_code": "LMRE-1G", "quantity": 2, "side": "SELL"})
    assert q.status_code == 400  # token sedang dicetak tidak bisa dijual

    assert staff.post(f"/v1/admin/print-orders/{order['id']}/pick", {"serial_number": "SALAH"}).json()["error"]["code"] == "SERIAL_MISMATCH"
    for item in order["items"]:
        staff.ok("post", f"/v1/admin/print-orders/{order['id']}/pick", {"serial_number": item["serial_number"]})
    assert user.post(f"/v1/print-orders/{order['id']}/cancel").json()["error"]["code"] == "INVALID_STATE"
    staff.ok("post", f"/v1/admin/print-orders/{order['id']}/ship", {"tracking_number": "INT-001", "carrier": "Internal"})
    for item in order["items"]:
        staff.ok("post", f"/v1/admin/print-orders/{order['id']}/arrive", {"serial_number": item["serial_number"]})

    ready = user.ok("get", f"/v1/print-orders/{order['id']}")
    assert ready["status"] == "READY_PICKUP" and ready["pickup_code"]
    admin_view = staff.ok("get", "/v1/admin/print-orders")[0]
    assert admin_view["pickup_code"] is None  # petugas harus meminta kode ke pengguna

    r = staff.post(f"/v1/admin/print-orders/{order['id']}/pickup", {"code": "000000", "identity_checked": True})
    assert r.json()["error"]["code"] == "INVALID_PICKUP_CODE"
    staff.ok("post", f"/v1/admin/print-orders/{order['id']}/pickup", {"code": ready["pickup_code"], "identity_checked": True})

    closed = user.ok("get", "/v1/tokens", params={"include_closed": True})
    assert {t["status"] for t in closed if t["code"] in codes} == {"PRINTED"}
    assert clean(db)


def test_batal_cetak_sebelum_diambil_mengembalikan_token(db, user):
    code = user.tokens()[0]["code"]
    order = user.ok("post", "/v1/print-orders", {"token_codes": [code], "method": "SHIP", "address": "Jl. Melati 1, Jakarta", "pin": PIN})
    user.ok("post", f"/v1/print-orders/{order['id']}/cancel")
    assert next(t for t in user.tokens() if t["code"] == code)["status"] == "ACTIVE"
    assert clean(db)
