import os
from datetime import UTC, datetime, timedelta

os.environ.setdefault("REIDIG_DATABASE_URL", "sqlite://")
os.environ.setdefault("REIDIG_AUTO_CREATE_SCHEMA", "false")

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text
from sqlalchemy.orm import sessionmaker

import app.models  # noqa: F401
from app.core import clock
from app.core.db import Base, get_db, make_engine
from app.core.guards import install_db_guards
from app.main import create_app
from app.seed import ADMIN_PASSWORD, DEMO_EMAIL, DEMO_PASSWORD, DEMO_PIN, seed_demo


class Clock:
    def __init__(self):
        self.value = datetime(2026, 11, 4, 3, 0, tzinfo=UTC)

    def __call__(self):
        return self.value

    def advance(self, **kw):
        self.value += timedelta(**kw)


@pytest.fixture
def fake_clock():
    c = Clock()
    clock.set_clock(c)
    yield c
    clock.set_clock(None)


@pytest.fixture
def Session(tmp_path, fake_clock):
    # REIDIG_TEST_DATABASE_URL=postgresql+psycopg://... untuk menguji di PostgreSQL (skema dikosongkan tiap tes).
    url = os.environ.get("REIDIG_TEST_DATABASE_URL")
    engine = make_engine(url or f"sqlite:///{tmp_path / 'test.db'}")
    if url:
        with engine.begin() as conn:
            conn.execute(text("DROP SCHEMA public CASCADE; CREATE SCHEMA public"))
    Base.metadata.create_all(engine)
    with engine.begin() as conn:
        install_db_guards(conn)
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    with factory() as db:
        seed_demo(db)
    yield factory
    engine.dispose()


@pytest.fixture
def db(Session):
    with Session() as s:
        yield s


def email_ticket(client: TestClient, email: str, purpose: str = "REGISTER") -> str:
    r = client.post("/v1/auth/email-code", json={"email": email, "purpose": purpose})
    assert r.status_code == 202, r.text
    r = client.post("/v1/auth/email-code/verify", json={"email": email, "purpose": purpose, "code": r.json()["dev_code"]})
    assert r.status_code == 200, r.text
    return r.json()["email_ticket"]


def register(client: TestClient, name: str, email: str, password: str = "rahasia123", **extra):
    body = {"email_ticket": email_ticket(client, email), "name": name, "email": email, "password": password, **extra}
    return client.post("/v1/auth/register", json=body)


class Api:
    def __init__(self, client: TestClient):
        self.client = client
        self.headers: dict[str, str] = {}

    def login(self, email=DEMO_EMAIL, password=DEMO_PASSWORD):
        r = self.client.post("/v1/auth/login", json={"email": email, "password": password})
        assert r.status_code == 200, r.text
        self.headers = {"Authorization": f"Bearer {r.json()['access_token']}"}
        return self

    def admin(self, slug: str):
        r = self.client.post("/v1/admin/auth/login", json={"email": f"{slug}@reidig.id", "password": ADMIN_PASSWORD})
        assert r.status_code == 200, r.text
        self.headers = {"Authorization": f"Bearer {r.json()['access_token']}"}
        return self

    def get(self, path, **kw):
        return self.client.get(path, headers={**self.headers, **kw.pop("headers", {})}, **kw)

    def post(self, path, json=None, **kw):
        return self.client.post(path, json=json, headers={**self.headers, **kw.pop("headers", {})}, **kw)

    def ok(self, method, path, json=None, status=None, **kw):
        r = getattr(self, method)(path, json=json, **kw) if method == "post" else self.get(path, **kw)
        assert r.status_code == (status or (200 if method == "get" else r.status_code)) and r.status_code < 300, r.text
        return r.json()

    # Alur yang sering dipakai
    def quote(self, sku="LMRE-0.1G", qty=1, side="BUY"):
        return self.ok("post", "/v1/quotes", {"sku_code": sku, "quantity": qty, "side": side})

    def buy(self, sku="LMRE-0.1G", qty=1, method="WALLET", **kw):
        q = self.quote(sku, qty)
        return self.post("/v1/orders/buy", {"quote_id": q["id"], "payment_method": method}, **kw)

    def wallet(self) -> int:
        return int(self.ok("get", "/v1/wallet")["available"])

    def tokens(self):
        return self.ok("get", "/v1/tokens")


@pytest.fixture
def client(Session):
    application = create_app(run_init=False)

    def _db():
        s = Session()
        try:
            yield s
        except Exception:
            s.rollback()
            raise
        finally:
            s.close()

    application.dependency_overrides[get_db] = _db
    with TestClient(application) as c:
        yield c


@pytest.fixture
def user(client):
    return Api(client).login()


@pytest.fixture
def admin(client):
    return lambda slug: Api(client).admin(slug)


@pytest.fixture
def new_user(client):
    """Pengguna baru yang sudah KYC dan punya PIN, tanpa saldo."""

    def make(email="baru@reidig.id", name="Budi Santoso"):
        api = Api(client)
        r = register(client, name, email)
        assert r.status_code == 201, r.text
        api.headers = {"Authorization": f"Bearer {r.json()['access_token']}"}
        api.ok("post", "/v1/kyc", {"full_name": name, "nik": "3171000000000999", "date_of_birth": "1995-05-05"})
        r = client.put("/v1/me/pin", json={"pin": "582913"}, headers=api.headers)
        assert r.status_code == 204, r.text
        return api

    return make


PIN = DEMO_PIN
