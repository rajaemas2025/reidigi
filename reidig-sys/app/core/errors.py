from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.exc import IntegrityError

STATUS_BY_CODE = {
    "UNAUTHENTICATED": 401,
    "INVALID_CREDENTIALS": 401,
    "INVALID_SIGNATURE": 401,
    "FORBIDDEN": 403,
    "KYC_REQUIRED": 403,
    "PIN_REQUIRED": 403,
    "INVALID_PIN": 403,
    "PIN_LOCKED": 423,
    "FEATURE_DISABLED": 403,
    "TRADING_HALTED": 423,
    "WITHDRAWALS_FROZEN": 423,
    "NOT_FOUND": 404,
    "ORDER_NOT_FOUND": 404,
    "TOKEN_NOT_OWNED": 404,
    "INSUFFICIENT_STOCK": 409,
    "INSUFFICIENT_BALANCE": 409,
    "DUPLICATE": 409,
    "INVALID_STATE": 409,
    "TOKEN_LOCKED": 409,
    "HOLDING_PERIOD_ACTIVE": 409,
    "QUOTE_EXPIRED": 409,
    "ORDER_EXPIRED": 409,
    "PRICE_CHANGED": 409,
    "PRICE_STALE": 409,
    "COUNT_IN_PROGRESS": 409,
    "SAME_COUNTER": 409,
    "MAKER_CHECKER": 409,
    "IDEMPOTENCY_KEY_REUSED": 422,
    "CONFLICT": 409,
    "LIMIT_EXCEEDED": 422,
}


class ApiError(Exception):
    def __init__(self, code: str, message: str, status: int | None = None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status = status or STATUS_BY_CODE.get(code, 400)


def fail(code: str, message: str):
    raise ApiError(code, message)


def _body(code: str, message: str, **extra) -> dict:
    return {"error": {"code": code, "message": message, **extra}}


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(ApiError)
    async def _api_error(_: Request, exc: ApiError):
        return JSONResponse(status_code=exc.status, content=_body(exc.code, exc.message))

    @app.exception_handler(RequestValidationError)
    async def _validation(_: Request, exc: RequestValidationError):
        details = [{"field": ".".join(str(p) for p in e["loc"][1:]), "message": e["msg"]} for e in exc.errors()]
        return JSONResponse(status_code=422, content=_body("VALIDATION_ERROR", "Periksa kembali isian Anda.", details=details))

    @app.exception_handler(IntegrityError)
    async def _integrity(_: Request, exc: IntegrityError):
        # Constraint atau trigger database menolak perubahan: invarian terjaga di level terbawah.
        return JSONResponse(status_code=409, content=_body("CONFLICT", "Permintaan bertabrakan dengan data lain. Coba lagi."))
