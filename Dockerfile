# =============================================================================
# Root Multi-Target Dockerfile untuk REIDIG Monorepo (Dokploy)
#
# Anda dapat memakai:
# 1. Dockerfile spesifik per folder (Direkomendasikan di Dokploy):
#    - API       -> ./reidig-sys/Dockerfile (Port 8000)
#    - Dashboard -> ./dashboard/Dockerfile  (Port 80)
#    - APK       -> ./android/Dockerfile    (Port 80)
# 2. Atau memakai root Dockerfile ini dengan Build Stage Target:
#    - target: api (default) | dashboard | apk
# =============================================================================

# -----------------------------------------------------------------------------
# Target 1: dashboard (Web Portal Operasional — Port 80)
# -----------------------------------------------------------------------------
FROM node:22-alpine AS dashboard-builder
WORKDIR /app
COPY dashboard/package*.json ./
RUN npm ci || npm install
COPY dashboard/ ./
RUN npm run build

FROM nginx:stable-alpine AS dashboard
COPY --from=dashboard-builder /app/dist /usr/share/nginx/html
RUN printf 'server {\n\
    listen 80;\n\
    server_name _;\n\
    root /usr/share/nginx/html;\n\
    index index.html;\n\
    location /health { return 200 '\''{"status":"ok"}'\''; add_header Content-Type application/json; }\n\
    location / { try_files $uri $uri/ /index.html; }\n\
}\n' > /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

# -----------------------------------------------------------------------------
# Target 2: apk (Android APK Builder & Download Portal — Port 80)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS apk-builder
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    DEBIAN_FRONTEND=noninteractive
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip && rm -rf /var/lib/apt/lists/*
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdtools.zip && \
    unzip -q /tmp/cmdtools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdtools.zip && \
    yes | sdkmanager --licenses > /dev/null && \
    sdkmanager "platform-tools" "platforms;android-36"
WORKDIR /workspace
COPY android/ /workspace/
RUN rm -f /workspace/local.properties && chmod +x /workspace/gradlew && \
    ./gradlew :app:assembleDebug --no-daemon --stacktrace

FROM nginx:stable-alpine AS apk
COPY --from=apk-builder /workspace/app/build/outputs/apk/debug/app-debug.apk /usr/share/nginx/html/reidig-goldin.apk
RUN cp /usr/share/nginx/html/reidig-goldin.apk /usr/share/nginx/html/app-debug.apk && \
    printf '<!DOCTYPE html><html lang="id"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Unduh APK Goldin</title><style>body{font-family:sans-serif;background:#FBF9F5;color:#19122B;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:24px}.card{max-width:420px;width:100%%;background:#fff;border:1px solid #E7E5F0;border-radius:24px;padding:32px 24px;text-align:center}h1{color:#371F76;margin:0 0 8px}p{color:#5E5873;font-size:14px;margin:0 0 24px}.btn{display:block;padding:14px 20px;border-radius:999px;background:#371F76;color:#fff;font-weight:700;text-decoration:none}</style></head><body><div class="card"><h1>Goldin — REIDIG</h1><p>Unduh APK Android Debug (Goldin Prototype v1)</p><a class="btn" href="/reidig-goldin.apk" download="reidig-goldin.apk">Unduh APK Android (.apk)</a></div></body></html>' > /usr/share/nginx/html/index.html && \
    printf 'server { listen 80; server_name _; root /usr/share/nginx/html; index index.html; location /health { return 200 '\''{"status":"ok"}'\''; add_header Content-Type application/json; } location ~* \\.apk$ { default_type application/vnd.android.package-archive; add_header Content-Disposition "attachment"; } location / { try_files $uri $uri/ /index.html; } }\n' > /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

# -----------------------------------------------------------------------------
# Target 3 (Default): api (FastAPI Backend — Port 8000)
# -----------------------------------------------------------------------------
FROM python:3.12-slim AS api
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=8000
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl libpq5 && rm -rf /var/lib/apt/lists/*
COPY reidig-sys/requirements.txt /app/requirements.txt
RUN pip install --upgrade pip && pip install -r requirements.txt
COPY reidig-sys/ /app/
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8000}/health || exit 1
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
