"""Cicil emas lewat mitra (spesifikasi 6.3)."""

from sqlalchemy import select, text

from app.financing.models import FinancingAgreement
from app.payments.models import Payment
from app.reconciliation.service import check_invariants
from tests.conftest import PIN


def clean(db):
    db.expire_all()
    return [r.id for r in check_invariants(db) if not r.ok] == []


def application_body(user, **override):
    agreement = user.ok("get", "/v1/financing/agreement")
    sim = user.ok("post", "/v1/financing/simulations", {"sku_code": "LMRE-5G", "down_payment": "2000000", "tenor_months": 6})
    body = {
        "sku_code": "LMRE-5G", "down_payment": "2000000", "tenor_months": 6, "expected_price": sim["price"],
        "agreement_version": agreement["version"], "agreement_hash": agreement["content_hash"],
        "agreed_terms": True, "agreed_data_sharing": True, "pin": PIN,
    }
    return {**body, **override}


def test_simulasi_tidak_mengubah_apa_pun(user):
    sim = user.ok("post", "/v1/financing/simulations", {"sku_code": "LMRE-5G", "down_payment": "2000000", "tenor_months": 6})
    assert int(sim["monthly_before_fees"]) == (int(sim["price"]) - 2_000_000) // 6


def test_tanpa_persetujuan_ditolak(user):
    r = user.post("/v1/financing/applications", application_body(user, agreed_data_sharing=False))
    assert r.json()["error"]["code"] == "AGREEMENT_REQUIRED"


def test_harga_berubah_ditolak(user):
    r = user.post("/v1/financing/applications", application_body(user, expected_price="1"))
    assert r.json()["error"]["code"] == "PRICE_CHANGED"


def test_saldo_kurang_tidak_membatalkan_lalu_bayar_qris(db, client, user):
    app = user.ok("post", "/v1/financing/applications", application_body(user, down_payment="5000000"))
    assert app["status"] == "RESERVED"
    proof = db.scalar(select(FinancingAgreement))
    assert proof.device_id is None and proof.content_hash and proof.down_payment == 5_000_000

    r = user.post(f"/v1/financing/applications/{app['id']}/down-payment", {"method": "WALLET", "pin": PIN})
    assert r.json()["error"]["code"] == "INSUFFICIENT_BALANCE"
    assert user.ok("get", "/v1/financing/applications")[0]["status"] == "RESERVED"

    dp = user.ok("post", f"/v1/financing/applications/{app['id']}/down-payment", {"method": "QRIS"})
    assert dp["contract"] is None and dp["payment"]["payment_code"]
    assert client.post(f"/v1/dev/payments/{dp['payment']['id']}/simulate-paid", headers=user.headers).json()["status"] == "PAID"

    [contract] = user.ok("get", "/v1/financing/contracts")
    token = user.ok("get", f"/v1/tokens/{contract['token_code']}")
    assert token["status"] == "LOCKED_INSTALLMENT" and contract["status"] == "ON_TIME"
    assert clean(db)


def test_token_cicilan_terkunci_sampai_lunas_dari_mitra(db, client, user, fake_clock):
    app = user.ok("post", "/v1/financing/applications", application_body(user))
    user.ok("post", f"/v1/financing/applications/{app['id']}/down-payment", {"method": "WALLET", "pin": PIN})
    [contract] = user.ok("get", "/v1/financing/contracts")
    fake_clock.advance(days=2)

    r = user.post("/v1/print-orders", {"token_codes": [contract["token_code"]], "method": "SHIP", "address": "Jl. A", "pin": PIN})
    assert r.json()["error"]["code"] == "TOKEN_LOCKED"

    event = {"event_id": "evt-1", "contract_ref": contract["contract_ref"], "type": "PAID_OFF"}
    assert client.post("/v1/dev/financing-partner/simulate", json=event).status_code == 200
    assert client.post("/v1/dev/financing-partner/simulate", json=event).status_code == 200  # webhook ganda aman
    assert user.ok("get", f"/v1/tokens/{contract['token_code']}")["status"] == "ACTIVE"
    assert user.ok("get", "/v1/financing/contracts")[0]["status"] == "PAID_OFF"
    assert clean(db)


def test_webhook_mitra_tanpa_tanda_tangan_ditolak(client):
    r = client.post("/v1/webhooks/financing-partner", content=b'{"event_id":"x"}', headers={"X-Signature": "palsu"})
    assert r.status_code == 401


def test_kedaluwarsa_melepas_keping_dan_bayar_terlambat_ke_saldo(db, client, user, admin, fake_clock):
    stock = lambda: next(s["stock"] for s in user.ok("get", "/v1/catalog/skus")["skus"] if s["code"] == "LMRE-5G")  # noqa: E731
    before_stock, before_balance = stock(), user.wallet()
    app = user.ok("post", "/v1/financing/applications", application_body(user))
    dp = user.ok("post", f"/v1/financing/applications/{app['id']}/down-payment", {"method": "VIRTUAL_ACCOUNT"})
    assert stock() == before_stock - 1

    fake_clock.advance(minutes=16)
    assert admin("finance").ok("post", "/v1/admin/jobs/sweep")["expired_applications"] == 1
    assert stock() == before_stock

    db.execute(text("UPDATE payments SET status='PENDING' WHERE id=:id"), {"id": dp["payment"]["id"]})
    db.commit()
    assert client.post(f"/v1/dev/payments/{dp['payment']['id']}/simulate-paid", headers=user.headers).json()["status"] == "REFUNDED"
    assert user.wallet() == before_balance + 2_000_000
    assert user.ok("get", "/v1/financing/contracts") == []
    assert db.get(Payment, dp["payment"]["id"]).status == "REFUNDED"
    assert clean(db)


def test_bukti_persetujuan_tidak_bisa_diubah(db, user):
    user.ok("post", "/v1/financing/applications", application_body(user))
    try:
        db.execute(text("UPDATE financing_agreements SET down_payment = 1"))
        raise AssertionError("seharusnya ditolak")
    except Exception as exc:
        assert "append-only" in str(exc)
