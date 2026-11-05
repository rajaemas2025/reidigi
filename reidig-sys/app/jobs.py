"""Job latar belakang. Dijalankan Celery beat (app/worker.py) atau manual lewat /v1/admin/jobs/sweep."""

from sqlalchemy.orm import Session

from app.financing import service as financing
from app.orders import service as orders
from app.reconciliation import service as reconciliation
from app.wallet import withdrawals


def run_sweeps(db: Session) -> dict:
    expired_orders = orders.sweep_expired(db)
    expired_applications = financing.sweep_expired(db)
    db.commit()
    return {"expired_orders": expired_orders, "expired_applications": expired_applications}


def run_disbursements(db: Session) -> int:
    sent = withdrawals.send_pending(db)
    db.commit()
    return sent


def run_reconciliation(db: Session) -> int:
    failed = sum(1 for r in reconciliation.run(db) if not r.ok)
    db.commit()
    return failed
