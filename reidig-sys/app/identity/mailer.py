"""Pengiriman email. Penyedia email belum terhubung.

Dev: kode hanya dicatat di log dan dikembalikan di respons API (`dev_code`).
Produksi: ditolak sampai penyedia email terpasang, supaya kode tidak hilang diam-diam.
"""

import logging

from app.core.config import get_settings
from app.core.errors import ApiError

log = logging.getLogger("reidig.mailer")

SUBJECTS = {
    "REGISTER": "Kode verifikasi akun REIDIG",
    "RESET_PASSWORD": "Kode atur ulang kata sandi REIDIG",
}


def send_email_code(email: str, purpose: str, code: str) -> None:
    if not get_settings().is_dev:
        raise ApiError("FEATURE_DISABLED", "Pengiriman email belum tersedia. Coba lagi nanti.")
    log.info("%s untuk %s: %s", SUBJECTS[purpose], email, code)
