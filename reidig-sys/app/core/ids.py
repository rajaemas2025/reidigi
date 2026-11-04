"""ID token: RET-<kode SKU>-<10 karakter acak Crockford Base32><1 check digit>."""

import re
import secrets

ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
_NORMALIZE = str.maketrans({"I": "1", "L": "1", "O": "0"})


def sku_part(sku_code: str) -> str:
    return re.sub(r"[^A-Z0-9]", "", sku_code.upper())


def _luhn_sum(chars: str, double_first: bool) -> int:
    total, double = 0, double_first
    for ch in reversed(chars):
        addend = ALPHABET.index(ch) * (2 if double else 1)
        total += addend // 32 + addend % 32
        double = not double
    return total


def check_symbol(body: str) -> str:
    """Luhn mod 32: menangkap semua salah satu karakter dan hampir semua dua karakter bersebelahan yang tertukar
    (kecuali pasangan 0 dan Z). Dipilih ketimbang check symbol Crockford mod 37 agar hasilnya tetap huruf/angka,
    tanpa simbol * ~ $ = yang menyulitkan dibaca dan dipakai di URL."""
    return ALPHABET[(32 - _luhn_sum(body, True) % 32) % 32]


def new_token_code(sku_code: str) -> str:
    body = "".join(secrets.choice(ALPHABET) for _ in range(10))  # CSPRNG
    return f"RET-{sku_part(sku_code)}-{body}{check_symbol(body)}"


def normalize_token_code(code: str) -> str:
    code = code.strip().upper()
    prefix, _, tail = code.rpartition("-")
    return f"{prefix}-{tail.translate(_NORMALIZE)}" if prefix else code


def is_valid_token_code(code: str) -> bool:
    parts = normalize_token_code(code).split("-")
    if len(parts) != 3 or parts[0] != "RET" or len(parts[2]) != 11:
        return False
    body, check = parts[2][:10], parts[2][10]
    return all(c in ALPHABET for c in body) and check_symbol(body) == check
