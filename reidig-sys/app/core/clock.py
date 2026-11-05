"""Sumber waktu tunggal (UTC). Pengujian bisa menggantinya lewat set_clock."""

from collections.abc import Callable
from contextlib import contextmanager
from datetime import UTC, datetime

_override: Callable[[], datetime] | None = None


def now() -> datetime:
    return _override() if _override else datetime.now(UTC)


def set_clock(fn: Callable[[], datetime] | None) -> None:
    global _override
    _override = fn


@contextmanager
def frozen_at(moment: datetime):
    """Sementara memakai waktu tertentu, lalu kembali ke sumber waktu sebelumnya."""
    global _override
    previous = _override
    _override = lambda: moment  # noqa: E731
    try:
        yield
    finally:
        _override = previous
