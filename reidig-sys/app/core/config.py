from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Semua nilai bisa diganti lewat variabel lingkungan berawalan REIDIG_."""

    model_config = SettingsConfigDict(env_prefix="REIDIG_", env_file=".env", extra="ignore")

    env: str = "dev"
    database_url: str = "sqlite:///./reidig.db"
    redis_url: str = "redis://localhost:6379/0"

    # Dev: buat tabel dan isi data contoh saat aplikasi mulai. Produksi memakai Alembic.
    auto_create_schema: bool = True
    seed_demo: bool = True

    quote_ttl_seconds: int = 60
    payment_window_seconds: int = 15 * 60
    holding_period_seconds: int = 24 * 60 * 60
    # 0 = tidak diperiksa. Aktifkan setelah job tarik harga berjalan.
    price_max_age_seconds: int = 0
    session_ttl_hours: int = 24 * 30
    max_pin_attempts: int = 5

    # Nilai sementara, menunggu keputusan bisnis.
    daily_buy_limit: int = 0  # 0 = tanpa batas
    min_withdrawal: int = 10_000
    topup_enabled: bool = False
    financing_partner_name: str = "Mitra pembiayaan"
    financing_tenors: list[int] = [3, 6, 12]

    pickup_min_weight_mg: int = 2_000
    pickup_deadline_days: int = 14
    finance_approval_mg: int = 100_000

    kyc_auto_approve: bool = True
    nik_pepper: str = "dev-nik-pepper"
    ifortepay_secret: str = "dev-ifortepay-secret"
    financing_partner_secret: str = "dev-partner-secret"

    @property
    def is_dev(self) -> bool:
        return self.env == "dev"


@lru_cache
def get_settings() -> Settings:
    return Settings()
