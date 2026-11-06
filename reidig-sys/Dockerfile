# =============================================================================
# REIDIG API Backend (FastAPI + SQLAlchemy + Alembic + Celery)
# Mendukung Build Context dari root repo (`.`) maupun subfolder (`./reidig-sys`)
# =============================================================================
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=8000

WORKDIR /app

# Install dependensi sistem (curl untuk healthcheck, libpq untuk PostgreSQL)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    libpq5 \
    && rm -rf /var/lib/apt/lists/*

# Salin context dan deteksi otomatis apakah context berasal dari root (`.`) atau `./reidig-sys`
COPY . /tmp/build-ctx
RUN if [ -f /tmp/build-ctx/reidig-sys/requirements.txt ]; then \
        cp -r /tmp/build-ctx/reidig-sys/. /app/; \
    else \
        cp -r /tmp/build-ctx/. /app/; \
    fi && rm -rf /tmp/build-ctx

RUN pip install --upgrade pip && \
    pip install -r requirements.txt

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8000}/health || exit 1

CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
