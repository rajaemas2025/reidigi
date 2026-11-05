"""Tipe API bersama. Rupiah dikirim sebagai string agar aman di klien; float ditolak."""

import re
from typing import Annotated

from pydantic import BaseModel, BeforeValidator, ConfigDict, PlainSerializer, WithJsonSchema

_INT = re.compile(r"^-?\d+$")


def _to_int(value):
    if isinstance(value, bool):
        raise ValueError("Nilai harus bilangan bulat")
    if isinstance(value, int):
        return value
    if isinstance(value, str) and _INT.match(value.strip()):
        return int(value.strip())
    raise ValueError("Nilai harus bilangan bulat tanpa desimal, contoh \"1450000\"")


Rupiah = Annotated[
    int,
    BeforeValidator(_to_int),
    PlainSerializer(lambda v: str(v), return_type=str),
    WithJsonSchema({"type": "string", "pattern": r"^-?\d+$", "examples": ["1450000"]}),
]


class Schema(BaseModel):
    model_config = ConfigDict(from_attributes=True)
