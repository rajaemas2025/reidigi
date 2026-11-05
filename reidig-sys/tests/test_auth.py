"""Daftar akun, masuk, keluar, dan atur ulang kata sandi."""

from app.seed import DEMO_EMAIL, DEMO_PASSWORD
from tests.conftest import email_ticket, register


def send_code(client, email, purpose="REGISTER"):
    return client.post("/v1/auth/email-code", json={"email": email, "purpose": purpose})


def verify(client, email, code, purpose="REGISTER"):
    return client.post("/v1/auth/email-code/verify", json={"email": email, "purpose": purpose, "code": code})


def auth(token):
    return {"Authorization": f"Bearer {token}"}


def test_daftar_lalu_masuk(client):
    r = register(client, "Sari Dewi", "Sari@Gmail.com", date_of_birth="1992-03-14")
    assert r.status_code == 201, r.text
    me = client.get("/v1/me", headers=auth(r.json()["access_token"])).json()
    assert me["email"] == "sari@gmail.com" and me["date_of_birth"] == "1992-03-14"
    assert me["verified"] is False and me["has_pin"] is False

    r = client.post("/v1/auth/login", json={"email": " SARI@gmail.com ", "password": "rahasia123"})
    assert r.status_code == 200, r.text
    assert client.get("/v1/wallet", headers=auth(r.json()["access_token"])).json()["available"] == "0"


def test_daftar_wajib_verifikasi_email(client):
    body = {"email_ticket": "palsu", "name": "Sari Dewi", "email": "sari@gmail.com", "password": "rahasia123"}
    r = client.post("/v1/auth/register", json=body)
    assert r.status_code == 422 and r.json()["error"]["code"] == "INVALID_CODE"
    r = client.post("/v1/auth/register", json={k: v for k, v in body.items() if k != "email_ticket"})
    assert r.status_code == 422 and r.json()["error"]["code"] == "VALIDATION_ERROR"


def test_tiket_hanya_untuk_email_yang_diverifikasi_dan_sekali_pakai(client):
    ticket = email_ticket(client, "a@reidig.id")
    body = {"email_ticket": ticket, "name": "Orang Lain", "email": "b@reidig.id", "password": "rahasia123"}
    assert client.post("/v1/auth/register", json=body).json()["error"]["code"] == "INVALID_CODE"
    body["email"] = "a@reidig.id"
    assert client.post("/v1/auth/register", json=body).status_code == 201
    body["email"] = "a@reidig.id"
    assert client.post("/v1/auth/register", json=body).json()["error"]["code"] in ("INVALID_CODE", "DUPLICATE")


def test_tiket_tidak_terpakai_kalau_isian_ditolak(client):
    ticket = email_ticket(client, "c@reidig.id")
    body = {"email_ticket": ticket, "name": "Citra", "email": "c@reidig.id", "password": "pendek"}
    assert client.post("/v1/auth/register", json=body).json()["error"]["code"] == "INVALID"
    body["password"] = "rahasia123"
    assert client.post("/v1/auth/register", json=body).status_code == 201


def test_daftar_menolak_di_bawah_18_tahun(client):
    r = register(client, "Anak Muda", "muda@reidig.id", date_of_birth="2015-01-01")
    assert r.json()["error"]["code"] == "INVALID"


def test_kode_email_tidak_dikirim_ke_email_terdaftar(client):
    r = send_code(client, DEMO_EMAIL)
    assert r.status_code == 409 and r.json()["error"]["code"] == "DUPLICATE"


def test_kode_salah_dibatasi(client):
    code = send_code(client, "d@reidig.id").json()["dev_code"]
    wrong = "000000" if code != "000000" else "111111"
    for left in (4, 3, 2, 1):
        assert verify(client, "d@reidig.id", wrong).json()["error"]["message"] == f"Kode salah. Sisa {left} percobaan."
    assert "Terlalu banyak" in verify(client, "d@reidig.id", wrong).json()["error"]["message"]
    # Kode yang benar pun tidak berlaku lagi.
    assert verify(client, "d@reidig.id", code).json()["error"]["code"] == "INVALID_CODE"


def test_kode_kedaluwarsa_dan_kirim_ulang(client, fake_clock):
    first = send_code(client, "e@reidig.id").json()["dev_code"]
    r = send_code(client, "e@reidig.id")
    assert r.status_code == 429 and r.json()["error"]["code"] == "TOO_MANY_REQUESTS"

    fake_clock.advance(seconds=31)
    second = send_code(client, "e@reidig.id").json()["dev_code"]
    if first != second:
        assert verify(client, "e@reidig.id", first).json()["error"]["code"] == "INVALID_CODE"

    fake_clock.advance(minutes=11)
    assert verify(client, "e@reidig.id", second).json()["error"]["code"] == "INVALID_CODE"


def test_batas_kirim_kode_per_jam(client, fake_clock):
    for _ in range(5):
        assert send_code(client, "f@reidig.id").status_code == 202
        fake_clock.advance(seconds=31)
    assert send_code(client, "f@reidig.id").status_code == 429


def test_masuk_salah_dan_keluar(client):
    r = client.post("/v1/auth/login", json={"email": DEMO_EMAIL, "password": "salah123"})
    assert r.status_code == 401 and r.json()["error"]["code"] == "INVALID_CREDENTIALS"
    token = client.post("/v1/auth/login", json={"email": DEMO_EMAIL, "password": DEMO_PASSWORD}).json()["access_token"]
    assert client.get("/v1/me", headers=auth(token)).status_code == 200
    assert client.post("/v1/auth/logout", headers=auth(token)).status_code == 204
    assert client.get("/v1/me", headers=auth(token)).status_code == 401


def test_sesi_kedaluwarsa(client, fake_clock):
    token = client.post("/v1/auth/login", json={"email": DEMO_EMAIL, "password": DEMO_PASSWORD}).json()["access_token"]
    fake_clock.advance(days=31)
    assert client.get("/v1/me", headers=auth(token)).json()["error"]["code"] == "UNAUTHENTICATED"


def test_atur_ulang_kata_sandi(client):
    old = client.post("/v1/auth/login", json={"email": DEMO_EMAIL, "password": DEMO_PASSWORD}).json()["access_token"]
    ticket = email_ticket(client, DEMO_EMAIL, "RESET_PASSWORD")
    r = client.post("/v1/auth/password-reset", json={"email_ticket": ticket, "new_password": "baru12345"})
    assert r.status_code == 204, r.text

    assert client.get("/v1/me", headers=auth(old)).status_code == 401
    assert client.post("/v1/auth/login", json={"email": DEMO_EMAIL, "password": DEMO_PASSWORD}).status_code == 401
    assert client.post("/v1/auth/login", json={"email": DEMO_EMAIL, "password": "baru12345"}).status_code == 200
    r = client.post("/v1/auth/password-reset", json={"email_ticket": ticket, "new_password": "lagi12345"})
    assert r.json()["error"]["code"] == "INVALID_CODE"


def test_lupa_sandi_tidak_membocorkan_email_terdaftar(client):
    r = send_code(client, "tidak-ada@reidig.id", "RESET_PASSWORD")
    assert r.status_code == 202 and r.json()["dev_code"] is None


def test_tiket_daftar_tidak_bisa_untuk_atur_ulang_sandi(client):
    ticket = email_ticket(client, "g@reidig.id")
    r = client.post("/v1/auth/password-reset", json={"email_ticket": ticket, "new_password": "baru12345"})
    assert r.json()["error"]["code"] == "INVALID_CODE"
