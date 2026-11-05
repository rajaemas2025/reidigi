"""
Klien iFortepay tiruan. Format permintaan, callback, dan tanda tangan masih asumsi:
HMAC-SHA256 atas body mentah, dikirim di header X-Signature. Sesuaikan dengan dokumentasi iFortepay.
"""

import secrets

from app.core.config import get_settings
from app.core.security import hmac_sha256, verify_hmac


def create_charge(method: str, amount: int, reference: str) -> tuple[str, str]:
    """Mengembalikan (provider_ref, kode pembayaran: isi QR atau nomor VA)."""
    provider_ref = f"IFP-{secrets.token_hex(8).upper()}"
    if method == "QRIS":
        code = f"00020101021226QRIS.REIDIG.{reference}.{amount}"
    else:
        code = "70070" + "".join(secrets.choice("0123456789") for _ in range(11))
    return provider_ref, code


def create_disbursement(amount: int, bank_code: str, account_number: str, reference: str) -> str:
    return f"IFD-{secrets.token_hex(8).upper()}"


def sign(body: bytes) -> str:
    return hmac_sha256(get_settings().ifortepay_secret, body)


def verify(body: bytes, signature: str | None) -> bool:
    return verify_hmac(get_settings().ifortepay_secret, body, signature)
