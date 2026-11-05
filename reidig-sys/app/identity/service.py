import re
import secrets
from datetime import date, timedelta

from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from app.admin.audit import audit
from app.core import clock
from app.core.config import get_settings
from app.core.errors import ApiError
from app.core.security import hash_secret, new_session_token, sha256_hex, verify_secret
from app.identity.mailer import send_email_code
from app.identity.models import BankAccount, Device, EmailCode, KycRecord, User, UserSession

EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]{2,}$")


def is_ascii_digits(value: str) -> bool:
    """str.isdigit() juga menerima angka Arab dan superskrip, jadi batasi ke 0-9."""
    return value.isascii() and value.isdigit()


# ----- Aturan, sama dengan android/shared Rules.kt -----


def is_valid_password(password: str) -> bool:
    return len(password) >= 8 and any(c.isalpha() for c in password) and any(c.isdigit() for c in password)


def is_weak_pin(pin: str) -> bool:
    if len(pin) != 6 or not is_ascii_digits(pin):
        return True
    if len(set(pin)) <= 2:
        return True
    steps = [int(b) - int(a) for a, b in zip(pin, pin[1:])]
    return all(s == 1 for s in steps) or all(s == -1 for s in steps)


def is_adult(dob: date, today: date) -> bool:
    age = today.year - dob.year - ((today.month, today.day) < (dob.month, dob.day))
    return age >= 18


def normalize_name(name: str) -> str:
    return " ".join(name.upper().split())


# ----- Akun dan sesi -----


def normalize_email(email: str) -> str:
    return email.strip().lower()


def validate_password(password: str) -> None:
    if not is_valid_password(password):
        raise ApiError("INVALID", "Kata sandi minimal 8 karakter dengan huruf dan angka.")


def create_user(
    db: Session, name: str, email: str, password: str, phone: str | None, dob: date | None = None
) -> User:
    """Validasi dan simpan akun. Pemanggil dari API wajib lewat `register` (email sudah diverifikasi)."""
    email = normalize_email(email)
    if not EMAIL.match(email):
        raise ApiError("INVALID", "Format email tidak valid.")
    if len(name.strip()) < 3:
        raise ApiError("INVALID", "Nama minimal 3 huruf.")
    if len(name.strip()) > 120:
        raise ApiError("INVALID", "Nama maksimal 120 karakter.")
    validate_password(password)
    if phone is not None and not (is_ascii_digits(phone) and phone.startswith("8") and 9 <= len(phone) <= 12):
        raise ApiError("INVALID", "Nomor HP ditulis setelah +62, tanpa angka 0 di depan.")
    if dob is not None and not is_adult(dob, clock.now().date()):
        raise ApiError("INVALID", "Pengguna minimal berusia 18 tahun.")
    if email_taken(db, email):
        raise ApiError("DUPLICATE", "Email sudah terdaftar.")
    user = User(name=name.strip(), email=email, phone=phone, date_of_birth=dob, password_hash=hash_secret(password))
    db.add(user)
    db.flush()
    return user


def register(
    db: Session, ticket: str, name: str, email: str, password: str, phone: str | None, dob: date | None
) -> User:
    user = create_user(db, name, email, password, phone, dob)
    consume_email_ticket(db, ticket, "REGISTER", user.email)
    return user


def email_taken(db: Session, email: str) -> bool:
    return db.scalar(select(User.id).where(User.email == normalize_email(email))) is not None


# ----- Kode verifikasi email -----

PURPOSES = ("REGISTER", "RESET_PASSWORD")


def request_email_code(db: Session, email: str, purpose: str) -> str | None:
    """Kirim kode 6 angka. Mengembalikan kode hanya di dev, untuk diuji tanpa penyedia email.

    Atur ulang kata sandi selalu tampak berhasil, supaya tidak bisa dipakai menebak email yang terdaftar.
    """
    settings = get_settings()
    email = normalize_email(email)
    if purpose not in PURPOSES:
        raise ApiError("INVALID", "Tujuan kode tidak dikenal.")
    if not EMAIL.match(email):
        raise ApiError("INVALID", "Format email tidak valid.")
    registered = email_taken(db, email)
    if purpose == "REGISTER" and registered:
        raise ApiError("DUPLICATE", "Email sudah terdaftar. Silakan masuk.")
    if purpose == "RESET_PASSWORD" and not registered:
        return None

    now = clock.now()
    last = latest_code(db, email, purpose)
    if last and last.created_at > now - timedelta(seconds=settings.email_code_resend_seconds):
        wait = int((last.created_at + timedelta(seconds=settings.email_code_resend_seconds) - now).total_seconds()) + 1
        raise ApiError("TOO_MANY_REQUESTS", f"Tunggu {wait} detik sebelum meminta kode baru.")
    sent_last_hour = db.scalar(
        select(func.count(EmailCode.id)).where(EmailCode.email == email, EmailCode.created_at > now - timedelta(hours=1))
    )
    if sent_last_hour >= settings.email_code_max_per_hour:
        raise ApiError("TOO_MANY_REQUESTS", "Terlalu banyak permintaan kode. Coba lagi dalam 1 jam.")

    # Kode lama tidak berlaku lagi setelah kode baru dikirim.
    db.execute(
        update(EmailCode)
        .where(EmailCode.email == email, EmailCode.purpose == purpose, EmailCode.consumed_at.is_(None))
        .values(consumed_at=now)
    )
    code = f"{secrets.randbelow(10**6):06d}"
    db.add(
        EmailCode(
            email=email,
            purpose=purpose,
            code_hash=hash_secret(code),
            created_at=now,
            expires_at=now + timedelta(seconds=settings.email_code_ttl_seconds),
        )
    )
    send_email_code(email, purpose, code)
    return code if settings.is_dev else None


def latest_code(db: Session, email: str, purpose: str) -> EmailCode | None:
    return db.scalar(
        select(EmailCode).where(EmailCode.email == email, EmailCode.purpose == purpose).order_by(EmailCode.id.desc()).limit(1)
    )


def verify_email_code(db: Session, email: str, purpose: str, code: str) -> str:
    """Kode benar: kembalikan tiket sekali pakai. Percobaan gagal langsung disimpan."""
    settings = get_settings()
    email = normalize_email(email)
    record = latest_code(db, email, purpose)
    now = clock.now()
    if (
        record is None
        or record.consumed_at
        or record.verified_at
        or record.expires_at <= now
        or record.attempts >= settings.email_code_max_attempts
    ):
        raise ApiError("INVALID_CODE", "Kode sudah tidak berlaku. Kirim ulang kode.")
    if not verify_secret(code.strip(), record.code_hash):
        record.attempts += 1
        remaining = settings.email_code_max_attempts - record.attempts
        db.commit()
        if remaining <= 0:
            raise ApiError("INVALID_CODE", "Terlalu banyak percobaan. Kirim ulang kode.")
        raise ApiError("INVALID_CODE", f"Kode salah. Sisa {remaining} percobaan.")
    ticket = new_session_token()
    record.verified_at = now
    record.ticket_hash = sha256_hex(ticket)
    return ticket


def consume_email_ticket(db: Session, ticket: str, purpose: str, email: str | None = None) -> str:
    """Tandai tiket terpakai dan kembalikan emailnya."""
    record = db.scalar(select(EmailCode).where(EmailCode.ticket_hash == sha256_hex(ticket)).with_for_update())
    expired = record is not None and record.verified_at + timedelta(seconds=get_settings().email_ticket_ttl_seconds) <= clock.now()
    if record is None or record.purpose != purpose or record.consumed_at or expired:
        raise ApiError("INVALID_CODE", "Verifikasi email sudah tidak berlaku. Ulangi dari awal.")
    if email is not None and record.email != normalize_email(email):
        raise ApiError("INVALID_CODE", "Email tidak sama dengan email yang diverifikasi.")
    record.consumed_at = clock.now()
    return record.email


def reset_password(db: Session, ticket: str, new_password: str) -> User:
    validate_password(new_password)
    email = consume_email_ticket(db, ticket, "RESET_PASSWORD")
    user = db.scalar(select(User).where(User.email == email))
    if user is None:
        raise ApiError("INVALID_CODE", "Verifikasi email sudah tidak berlaku. Ulangi dari awal.")
    user.password_hash = hash_secret(new_password)
    # Semua perangkat harus masuk lagi dengan kata sandi baru.
    db.execute(
        update(UserSession).where(UserSession.user_id == user.id, UserSession.revoked_at.is_(None)).values(revoked_at=clock.now())
    )
    audit(db, user, "PASSWORD_RESET", f"Pengguna #{user.id}")
    return user


# ----- Sesi -----


def login(db: Session, email: str, password: str, device_id: str | None, user_agent: str | None) -> tuple[User, str]:
    user = db.scalar(select(User).where(User.email == normalize_email(email)))
    if user is None or not verify_secret(password, user.password_hash):
        raise ApiError("INVALID_CREDENTIALS", "Email atau kata sandi salah. Coba lagi.")
    token = new_session_token()
    now = clock.now()
    db.add(
        UserSession(
            user_id=user.id,
            token_hash=sha256_hex(token),
            device_id=device_id,
            expires_at=now + timedelta(hours=get_settings().session_ttl_hours),
        )
    )
    if device_id:
        device = db.scalar(select(Device).where(Device.user_id == user.id, Device.device_id == device_id))
        if device:
            device.last_seen_at = now
            device.user_agent = user_agent
        else:
            db.add(Device(user_id=user.id, device_id=device_id, user_agent=user_agent))
    return user, token


def user_from_token(db: Session, token: str) -> User | None:
    session = db.scalar(select(UserSession).where(UserSession.token_hash == sha256_hex(token)))
    if session is None or session.revoked_at or session.expires_at <= clock.now():
        return None
    return db.get(User, session.user_id)


def logout(db: Session, token: str) -> None:
    session = db.scalar(select(UserSession).where(UserSession.token_hash == sha256_hex(token)))
    if session and not session.revoked_at:
        session.revoked_at = clock.now()


# ----- PIN -----


def set_pin(db: Session, user: User, pin: str, old_pin: str | None) -> None:
    if user.pin_hash:
        if old_pin is None:
            raise ApiError("PIN_REQUIRED", "Masukkan PIN lama untuk mengganti PIN.")
        verify_pin(db, user, old_pin)
    if is_weak_pin(pin):
        raise ApiError("WEAK_PIN", "PIN terlalu mudah ditebak. Hindari angka berulang dan berurutan.")
    user.pin_hash = hash_secret(pin)
    user.pin_failed_attempts = 0


def verify_pin(db: Session, user: User, pin: str | None) -> None:
    """Panggil sebelum perubahan lain dalam transaksi: percobaan gagal langsung disimpan."""
    if not user.pin_hash:
        raise ApiError("PIN_REQUIRED", "Buat PIN terlebih dahulu.")
    if user.pin_locked:
        raise ApiError("PIN_LOCKED", "PIN terkunci karena terlalu banyak percobaan. Hubungi layanan pelanggan.")
    if not pin:
        raise ApiError("PIN_REQUIRED", "Masukkan PIN.")
    if verify_secret(pin, user.pin_hash):
        if user.pin_failed_attempts:
            user.pin_failed_attempts = 0
        return
    user.pin_failed_attempts += 1
    remaining = get_settings().max_pin_attempts - user.pin_failed_attempts
    if remaining <= 0:
        user.pin_locked = True
        audit(db, user, "PIN_LOCKED", f"Pengguna #{user.id}", "Terlalu banyak percobaan")
    db.commit()
    if remaining <= 0:
        raise ApiError("PIN_LOCKED", "PIN terkunci karena terlalu banyak percobaan. Hubungi layanan pelanggan.")
    raise ApiError("INVALID_PIN", f"PIN salah. Sisa {remaining} percobaan.")


def require_verified(user: User) -> None:
    if user.kyc_status != "APPROVED":
        raise ApiError("KYC_REQUIRED", "Verifikasi identitas diperlukan terlebih dahulu.")


# ----- KYC -----


def submit_kyc(db: Session, user: User, full_name: str, nik: str, dob: date) -> KycRecord:
    """Vendor KYC belum terhubung. Dev: langsung disetujui; produksi: menunggu Compliance atau vendor."""
    settings = get_settings()
    if user.kyc_status in ("PENDING", "APPROVED"):
        raise ApiError("INVALID_STATE", "Verifikasi identitas sudah diajukan.")
    if not (len(nik) == 16 and is_ascii_digits(nik)):
        raise ApiError("INVALID", "NIK harus 16 digit angka.")
    if len(full_name.strip()) < 3 or not any(c.isalpha() for c in full_name):
        raise ApiError("INVALID", "Nama sesuai KTP minimal 3 huruf.")
    if len(full_name.strip()) > 120:
        raise ApiError("INVALID", "Nama sesuai KTP maksimal 120 karakter.")
    if not is_adult(dob, clock.now().date()):
        raise ApiError("INVALID", "Pengguna minimal berusia 18 tahun.")
    nik_hash = sha256_hex(settings.nik_pepper + nik)
    if nik_used_by_other(db, nik_hash, user.id):
        raise ApiError("DUPLICATE", "NIK ini sudah dipakai akun lain. Hubungi layanan pelanggan.")
    status = "APPROVED" if settings.kyc_auto_approve else "PENDING"
    record = KycRecord(
        user_id=user.id,
        full_name=full_name.strip(),
        nik_hash=nik_hash,
        nik_last4=nik[-4:],
        date_of_birth=dob,
        status=status,
        reason=None if status == "APPROVED" else "Menunggu pemeriksaan",
        decided_at=clock.now() if status == "APPROVED" else None,
    )
    db.add(record)
    user.kyc_status = status
    db.flush()
    return record


def nik_used_by_other(db: Session, nik_hash: str, user_id: int) -> bool:
    return db.scalar(
        select(KycRecord.id).where(KycRecord.nik_hash == nik_hash, KycRecord.user_id != user_id, KycRecord.status == "APPROVED").limit(1)
    ) is not None


def decide_kyc(db: Session, actor, kyc_id: int, approve: bool, note: str) -> KycRecord:
    record = db.get(KycRecord, kyc_id, with_for_update=True)
    if record is None or record.status != "PENDING":
        raise ApiError("INVALID_STATE", "Kasus ini tidak aktif.")
    if not approve and not note.strip():
        raise ApiError("INVALID", "Alasan penolakan wajib diisi.")
    if approve and nik_used_by_other(db, record.nik_hash, record.user_id):
        raise ApiError("DUPLICATE", "NIK ini sudah disetujui untuk akun lain.")
    record.status = "APPROVED" if approve else "REJECTED"
    record.decision_note = note.strip() or None
    record.decided_by_admin_id = actor.id
    record.decided_at = clock.now()
    db.get(User, record.user_id).kyc_status = record.status
    audit(db, actor, "KYC_APPROVED" if approve else "KYC_REJECTED", f"KYC #{kyc_id}", note.strip())
    return record


def approved_kyc(db: Session, user_id: int) -> KycRecord | None:
    return db.scalar(
        select(KycRecord).where(KycRecord.user_id == user_id, KycRecord.status == "APPROVED").order_by(KycRecord.id.desc()).limit(1)
    )


# ----- Rekening bank -----


def add_bank_account(db: Session, user: User, bank_code: str, account_number: str, holder_name: str) -> BankAccount:
    """Nama pemilik rekening wajib sama dengan nama KTP, untuk mencegah dana keluar ke pihak lain."""
    require_verified(user)
    account_number = account_number.replace(" ", "")
    if not is_ascii_digits(account_number) or not 6 <= len(account_number) <= 20:
        raise ApiError("INVALID", "Nomor rekening tidak valid.")
    kyc = approved_kyc(db, user.id)
    if kyc is None or normalize_name(kyc.full_name) != normalize_name(holder_name):
        raise ApiError("NAME_MISMATCH", "Nama pemilik rekening harus sama dengan nama di KTP.")
    exists = db.scalar(
        select(BankAccount.id).where(
            BankAccount.user_id == user.id, BankAccount.bank_code == bank_code.upper(), BankAccount.account_number == account_number
        )
    )
    if exists:
        raise ApiError("DUPLICATE", "Rekening ini sudah terdaftar.")
    # Pengecekan nama ke bank (inquiry) menunggu integrasi iFortepay.
    account = BankAccount(user_id=user.id, bank_code=bank_code.upper(), account_number=account_number, holder_name=holder_name.strip(), verified=True)
    db.add(account)
    db.flush()
    return account
