"""
Worker Celery dengan Redis.
  celery -A app.worker worker --beat --loglevel=info
"""

from celery import Celery

from app import jobs
from app.core.config import get_settings
from app.core.db import SessionLocal

settings = get_settings()
celery = Celery("reidig", broker=settings.redis_url, backend=settings.redis_url)
celery.conf.timezone = "UTC"
celery.conf.beat_schedule = {
    "sweep-expired": {"task": "app.worker.sweep_expired", "schedule": 30.0},
    "send-disbursements": {"task": "app.worker.send_disbursements", "schedule": 30.0},
    "reconcile": {"task": "app.worker.reconcile", "schedule": 300.0},
}


@celery.task
def sweep_expired() -> dict:
    with SessionLocal() as db:
        return jobs.run_sweeps(db)


@celery.task
def send_disbursements() -> int:
    with SessionLocal() as db:
        return jobs.run_disbursements(db)


@celery.task
def reconcile() -> int:
    with SessionLocal() as db:
        return jobs.run_reconciliation(db)
