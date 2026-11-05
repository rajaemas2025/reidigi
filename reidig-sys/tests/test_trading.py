"""Beli, jual, pembayaran gateway, dan saldo (spesifikasi 6.1, 6.2, 6.2a)."""

import json

from sqlalchemy import select

from app.payments import gateway
from app.reconciliation.service import check_invariants
from tests.conftest import PIN, register


def clean(db):
    db.expire_all()
    bad = [(r.id, r.detail) for r in check_invariants(db) if not r.ok]
    assert bad == []
    return True


def stock(user, sku="LMRE-1G"):
    return next(s["stock"] for s in user.ok("get", "/v1/catalog/skus")["skus"] if s["code"] == sku)


def callback(client, provider_ref, amount, status="PAID", signature=None):
    body = json.dumps({"provider_ref": provider_ref, "status": status, "amount": str(amount)}).encode()
    return client.post("/v1/webhooks/ifortepay/payments", content=body, headers={"X-Signature": signature or gateway.sign(body)})


def test_katalog_mengirim_rupiah_sebagai_string(user):
    sku = user.ok("get", "/v1/catalog/skus")["skus"][0]
    assert isinstance(sku["buy_price"], str) and sku["buy_price"].isdigit()


def test_rupiah_desimal_ditolak(user):
    r = user.post("/v1/wallet/withdrawals", {"amount": 50000.5, "bank_account_id": 1, "pin": PIN})
    assert r.status_code == 422


def test_beli_dengan_saldo_langsung_terbit_token(db, user):
    before, stock_before = user.wallet(), stock(user, "LMRE-0.1G")
    r = user.buy("LMRE-0.1G", 2)
    assert r.status_code == 201, r.text
    order = r.json()
    assert order["status"] == "PAID" and len(order["token_codes"]) == 2
    assert user.wallet() == before - int(order["gross"])
    assert stock(user, "LMRE-0.1G") == stock_before - 2
    assert clean(db)


def test_saldo_kurang_ditolak_dan_keping_tidak_tertahan(db, new_user):
    api = new_user()
    stock_before = stock(api)
    r = api.buy("LMRE-1G")
    assert r.json()["error"]["code"] == "INSUFFICIENT_BALANCE"
    assert stock(api) == stock_before


def test_stok_kurang_409(user):
    r = user.post("/v1/quotes", {"sku_code": "LMWD-1G", "quantity": 1})
    assert r.status_code == 409 and r.json()["error"]["code"] == "INSUFFICIENT_STOCK"


def test_quote_kedaluwarsa(user, fake_clock):
    q = user.quote()
    fake_clock.advance(seconds=61)
    r = user.post("/v1/orders/buy", {"quote_id": q["id"], "payment_method": "WALLET"})
    assert r.json()["error"]["code"] == "QUOTE_EXPIRED"


def test_quote_tidak_bisa_dipakai_dua_kali(user):
    q = user.quote()
    assert user.post("/v1/orders/buy", {"quote_id": q["id"], "payment_method": "WALLET"}).status_code == 201
    r = user.post("/v1/orders/buy", {"quote_id": q["id"], "payment_method": "WALLET"})
    assert r.json()["error"]["code"] == "QUOTE_EXPIRED"


def test_belum_kyc_tidak_bisa_beli(client):
    from tests.conftest import Api

    api = Api(client)
    r = register(client, "Tanpa KYC", "x@reidig.id")
    api.headers = {"Authorization": f"Bearer {r.json()['access_token']}"}
    assert api.buy().json()["error"]["code"] == "KYC_REQUIRED"


def test_idempotency_key_tidak_menagih_dua_kali(user):
    q = user.quote()
    body = {"quote_id": q["id"], "payment_method": "WALLET"}
    first = user.post("/v1/orders/buy", body, headers={"Idempotency-Key": "abc"})
    balance = user.wallet()
    again = user.post("/v1/orders/buy", body, headers={"Idempotency-Key": "abc"})
    assert again.json() == first.json() and user.wallet() == balance
    other = user.post("/v1/orders/buy", {**body, "payment_method": "QRIS"}, headers={"Idempotency-Key": "abc"})
    assert other.json()["error"]["code"] == "IDEMPOTENCY_KEY_REUSED"


def test_qris_menunggu_bayar_lalu_callback_menerbitkan_token(db, client, user):
    stock_before = stock(user)
    order = user.buy("LMRE-1G", 1, "QRIS").json()
    assert order["status"] == "PENDING_PAYMENT" and order["payment"]["payment_code"]
    assert stock(user) == stock_before - 1  # keping ditahan selama batas bayar

    from app.payments.models import Payment

    payment = db.get(Payment, order["payment"]["id"])
    assert callback(client, payment.provider_ref, payment.amount, signature="salah").status_code == 401
    assert callback(client, payment.provider_ref, payment.amount - 1).json()["error"]["code"] == "AMOUNT_MISMATCH"
    assert callback(client, payment.provider_ref, payment.amount).json()["status"] == "PAID"
    assert callback(client, payment.provider_ref, payment.amount).status_code == 200  # callback ganda aman

    paid = user.ok("get", f"/v1/orders/{order['id']}")
    assert paid["status"] == "PAID" and len(paid["token_codes"]) == 1
    assert clean(db)


def test_order_kedaluwarsa_disapu_dan_keping_kembali(db, user, admin, fake_clock):
    stock_before = stock(user)
    order = user.buy("LMRE-1G", 1, "VIRTUAL_ACCOUNT").json()
    fake_clock.advance(minutes=16)
    assert admin("finance").ok("post", "/v1/admin/jobs/sweep")["expired_orders"] == 1
    assert user.ok("get", f"/v1/orders/{order['id']}")["status"] == "EXPIRED"
    assert stock(user) == stock_before
    assert clean(db)


def test_bayar_setelah_kedaluwarsa_tidak_terbit_token_dan_dana_ke_saldo(db, client, user, fake_clock):
    from app.payments.models import Payment

    before = user.wallet()
    order = user.buy("LMRE-1G", 1, "QRIS").json()
    payment = db.get(Payment, order["payment"]["id"])
    fake_clock.advance(minutes=20)
    assert callback(client, payment.provider_ref, payment.amount).json()["status"] == "REFUNDED"

    result = user.ok("get", f"/v1/orders/{order['id']}")
    assert result["status"] == "PAID_AFTER_EXPIRY" and result["token_codes"] == []
    assert user.wallet() == before + payment.amount
    assert clean(db)


def test_batal_order_melepas_keping(user):
    stock_before = stock(user)
    order = user.buy("LMRE-1G", 1, "QRIS").json()
    assert user.ok("post", f"/v1/orders/{order['id']}/cancel")["status"] == "CANCELLED"
    assert stock(user) == stock_before


def test_jual_ditahan_24_jam_lalu_dana_masuk_saldo(db, user, fake_clock):
    code = user.buy("LMRE-0.5G").json()["token_codes"][0]
    q = user.quote("LMRE-0.5G", 1, "SELL")
    r = user.post("/v1/orders/sell", {"quote_id": q["id"], "token_codes": [code], "pin": PIN})
    assert r.json()["error"]["code"] == "HOLDING_PERIOD_ACTIVE"

    fake_clock.advance(hours=24)
    before, stock_before = user.wallet(), stock(user, "LMRE-0.5G")
    q = user.quote("LMRE-0.5G", 1, "SELL")
    sold = user.ok("post", "/v1/orders/sell", {"quote_id": q["id"], "token_codes": [code], "pin": PIN})
    assert sold["status"] == "COMPLETED" and sold["tax"] == "0"
    assert user.wallet() == before + int(sold["net"])
    assert stock(user, "LMRE-0.5G") == stock_before + 1
    assert code not in [t["code"] for t in user.tokens()]
    assert clean(db)


def test_jual_di_atas_10_juta_dipotong_pph22(db, user, admin, fake_clock):
    from app.wallet import service as wallet
    from app.ledger import service as ledger

    # Tambah saldo demo agar bisa membeli 5 gr.
    wallet.credit(db, 1, 20_000_000, "TOPUP", "TEST", "pph")
    ledger.post(db, [(ledger.CUSTOMER_FUNDS, 20_000_000, 0), (ledger.WALLET_LIABILITY, 0, 20_000_000)], "TEST", "pph")
    db.commit()
    code = user.buy("LMRE-5G").json()["token_codes"][0]
    fake_clock.advance(days=1)
    q = user.quote("LMRE-5G", 1, "SELL")
    sold = user.ok("post", "/v1/orders/sell", {"quote_id": q["id"], "token_codes": [code], "pin": PIN})
    gross = int(sold["gross"])
    assert gross > 10_000_000 and int(sold["tax"]) == gross * 150 // 10_000
    assert clean(db)


def test_token_orang_lain_tidak_bisa_dijual(user, new_user, fake_clock):
    code = user.tokens()[0]["code"]
    other = new_user()
    r = other.get(f"/v1/tokens/{code}")
    assert r.status_code == 404


def test_pin_terkunci_setelah_lima_kali_salah(user):
    code = user.tokens()[0]["code"]
    q = user.quote("LMRE-1G", 1, "SELL")
    for i in range(4):
        r = user.post("/v1/orders/sell", {"quote_id": q["id"], "token_codes": [code], "pin": "000001"})
        assert r.json()["error"]["code"] == "INVALID_PIN"
    r = user.post("/v1/orders/sell", {"quote_id": q["id"], "token_codes": [code], "pin": "000001"})
    assert r.json()["error"]["code"] == "PIN_LOCKED"
    r = user.post("/v1/orders/sell", {"quote_id": q["id"], "token_codes": [code], "pin": PIN})
    assert r.json()["error"]["code"] == "PIN_LOCKED"
    assert user.ok("get", "/v1/me")["pin_locked"]


def test_tarik_dana_ditahan_lalu_selesai_atau_kembali(db, client, user):
    from app.payments.models import Disbursement

    before = user.wallet()
    w = user.ok("post", "/v1/wallet/withdrawals", {"amount": "100000", "bank_account_id": 1, "pin": PIN})
    assert w["status"] == "PROCESSING"
    assert user.ok("get", "/v1/wallet") == {"available": str(before - 100_000), "held": "100000"}

    d = db.scalar(select(Disbursement).where(Disbursement.withdrawal_id == w["id"]))
    assert d.status == "SENT"
    r = client.post(f"/v1/dev/disbursements/{d.id}/simulate", json={"status": "FAILED"})
    assert r.json()["status"] == "FAILED"
    assert user.ok("get", "/v1/wallet") == {"available": str(before), "held": "0"}

    w2 = user.ok("post", "/v1/wallet/withdrawals", {"amount": "100000", "bank_account_id": 1, "pin": PIN})
    d2 = db.scalar(select(Disbursement).where(Disbursement.withdrawal_id == w2["id"]))
    client.post(f"/v1/dev/disbursements/{d2.id}/simulate", json={"status": "SUCCESS"})
    assert user.ok("get", "/v1/wallet") == {"available": str(before - 100_000), "held": "0"}
    assert clean(db)


def test_status_belum_pasti_tidak_bisa_dicoba_ulang(db, client, user, admin):
    from app.payments.models import Disbursement

    w = user.ok("post", "/v1/wallet/withdrawals", {"amount": "100000", "bank_account_id": 1, "pin": PIN})
    d = db.scalar(select(Disbursement).where(Disbursement.withdrawal_id == w["id"]))
    client.post(f"/v1/dev/disbursements/{d.id}/simulate", json={"status": "TIMEOUT"})
    finance = admin("finance")
    assert finance.post(f"/v1/admin/withdrawals/{w['id']}/retry").json()["error"]["code"] == "INVALID_STATE"
    resolved = finance.ok("post", f"/v1/admin/withdrawals/{w['id']}/resolve", {"succeeded": True, "note": "Mutasi bank dicek"})
    assert resolved["status"] == "SUCCESS"
    assert clean(db)


def test_henti_perdagangan_maker_checker(user, admin):
    a, b = admin("treasury-a"), admin("treasury-b")
    assert a.post("/v1/admin/trading/halt", {"reason": "Harga sumber tidak wajar"}).status_code == 204
    assert user.post("/v1/quotes", {"sku_code": "LMRE-1G", "quantity": 1}).json()["error"]["code"] == "TRADING_HALTED"
    assert a.post("/v1/admin/trading/resume", {}).json()["error"]["code"] == "MAKER_CHECKER"
    assert b.post("/v1/admin/trading/resume", {}).status_code == 204
    assert user.post("/v1/quotes", {"sku_code": "LMRE-1G", "quantity": 1}).status_code == 201


def test_peran_tanpa_wewenang_ditolak(admin):
    r = admin("gudang-a").post("/v1/admin/trading/halt", {"reason": "x"})
    assert r.status_code == 403


def test_riwayat_transaksi(user):
    user.buy()
    tx = user.ok("get", "/v1/transactions")
    assert tx[0]["type"] == "BUY" and tx[0]["amount"].startswith("-")


def test_kyc_menolak_angka_non_ascii(client):
    r = register(client, "Unicode", "u@reidig.id")
    h = {"Authorization": f"Bearer {r.json()['access_token']}"}
    body = {"full_name": "Budi Santoso", "nik": "٣١٧١٠٠٠٠٠٠٠٠٠٩٩٩", "date_of_birth": "1995-05-05"}
    assert client.post("/v1/kyc", json=body, headers=h).json()["error"]["code"] == "INVALID"
    assert client.put("/v1/me/pin", json={"pin": "²³⁴567"}, headers=h).json()["error"]["code"] == "WEAK_PIN"


def test_kyc_nama_terlalu_panjang(client):
    r = register(client, "Panjang", "l@reidig.id")
    h = {"Authorization": f"Bearer {r.json()['access_token']}"}
    body = {"full_name": "A" * 121, "nik": "3171000000000111", "date_of_birth": "1995-05-05"}
    assert client.post("/v1/kyc", json=body, headers=h).json()["error"]["code"] == "INVALID"


def test_compliance_tidak_bisa_setujui_nik_ganda(client, admin):
    from app.core.config import get_settings

    settings = get_settings()
    settings.kyc_auto_approve = False
    try:
        ids = []
        for email in ("g1@reidig.id", "g2@reidig.id"):
            r = register(client, "Ganda", email)
            h = {"Authorization": f"Bearer {r.json()['access_token']}"}
            body = {"full_name": "Ganda", "nik": "3171000000000777", "date_of_birth": "1995-05-05"}
            ids.append(client.post("/v1/kyc", json=body, headers=h).json()["id"])
        a = admin("compliance")
        ok = a.client.post(f"/v1/admin/kyc/{ids[0]}/decision", json={"approve": True, "note": ""}, headers=a.headers)
        assert ok.status_code == 200, ok.text
        dup = a.client.post(f"/v1/admin/kyc/{ids[1]}/decision", json={"approve": True, "note": ""}, headers=a.headers)
        assert dup.json()["error"]["code"] == "DUPLICATE"
    finally:
        settings.kyc_auto_approve = True
