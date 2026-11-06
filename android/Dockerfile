# =============================================================================
# REIDIG Android APK Builder & Download Server (Goldin Prototype v1)
# Mendukung Build Context dari root repo (`.`) maupun subfolder (`./android`)
# Stage 1: Build APK dengan JDK 21 + Android SDK 36
# Stage 2: Sajikan halaman unduh APK via Nginx pada port 80
# =============================================================================
FROM eclipse-temurin:21-jdk AS apk-builder

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    DEBIAN_FRONTEND=noninteractive
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Install Android Command Line Tools & terima seluruh lisensi SDK
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdtools.zip && \
    unzip -q /tmp/cmdtools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdtools.zip && \
    yes | sdkmanager --licenses > /dev/null && \
    sdkmanager "platform-tools" "platforms;android-36"

WORKDIR /workspace

COPY . /tmp/build-ctx
RUN if [ -f /tmp/build-ctx/android/gradlew ]; then \
        cp -r /tmp/build-ctx/android/. /workspace/; \
    else \
        cp -r /tmp/build-ctx/. /workspace/; \
    fi && rm -rf /tmp/build-ctx && \
    rm -f /workspace/local.properties && \
    chmod +x /workspace/gradlew

RUN ./gradlew :app:assembleDebug --no-daemon --stacktrace

# Stage 2: Nginx Web Server untuk Unduh APK di Dokploy
FROM nginx:stable-alpine

COPY --from=apk-builder /workspace/app/build/outputs/apk/debug/app-debug.apk /usr/share/nginx/html/reidig-goldin.apk
RUN cp /usr/share/nginx/html/reidig-goldin.apk /usr/share/nginx/html/app-debug.apk

RUN printf '<!DOCTYPE html>\n\
<html lang="id">\n\
<head>\n\
  <meta charset="UTF-8" />\n\
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />\n\
  <title>Unduh APK Goldin — REIDIG</title>\n\
  <style>\n\
    * { box-sizing: border-box; margin: 0; padding: 0; }\n\
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #FBF9F5; color: #19122B; display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 24px; }\n\
    .card { max-width: 440px; width: 100%%; background: #FFFFFF; border: 1px solid #E7E5F0; border-radius: 24px; padding: 32px 24px; text-align: center; box-shadow: 0 12px 32px rgba(55,31,118,0.08); }\n\
    .badge { display: inline-flex; align-items: center; justify-content: center; width: 64px; height: 64px; border-radius: 20px; background: #371F76; color: #E1C162; font-weight: 900; font-size: 24px; margin-bottom: 16px; }\n\
    h1 { color: #371F76; font-size: 26px; font-weight: 900; margin-bottom: 8px; }\n\
    p { color: #5E5873; font-size: 14px; line-height: 1.5; margin-bottom: 24px; }\n\
    .btn { display: block; width: 100%%; padding: 15px 20px; border-radius: 999px; background: #371F76; color: #F6F6FF; font-weight: 700; font-size: 15px; text-decoration: none; margin-bottom: 16px; }\n\
    .btn:hover { background: #432693; }\n\
    .meta { background: #F6F6FF; border: 1px solid #ECEAFE; border-radius: 14px; padding: 14px; text-align: left; font-size: 12.5px; color: #5E5873; line-height: 1.6; }\n\
    .meta strong { color: #19122B; }\n\
  </style>\n\
</head>\n\
<body>\n\
  <div class="card">\n\
    <div class="badge">24K</div>\n\
    <h1>Goldin — REIDIG</h1>\n\
    <p>Aplikasi Tabungan &amp; Cicilan Emas Fisik 24K Raja Emas Indonesia (Android Debug APK).</p>\n\
    <a class="btn" href="/reidig-goldin.apk" download="reidig-goldin.apk">Unduh APK Android (.apk)</a>\n\
    <div class="meta">\n\
      <div><strong>PIN Transaksi Demo:</strong> 147258</div>\n\
      <div><strong>Kode OTP Email Demo:</strong> 654321</div>\n\
      <div><strong>Kode OTP HP (WA/SMS):</strong> 123456</div>\n\
    </div>\n\
  </div>\n\
</body>\n\
</html>\n' > /usr/share/nginx/html/index.html

RUN printf 'server {\n\
    listen 80;\n\
    server_name _;\n\
    root /usr/share/nginx/html;\n\
    index index.html;\n\
\n\
    location /health {\n\
        access_log off;\n\
        add_header Content-Type application/json;\n\
        return 200 '\''{"status":"ok"}'\'';\n\
    }\n\
\n\
    location ~* \\.apk$ {\n\
        default_type application/vnd.android.package-archive;\n\
        add_header Content-Disposition "attachment";\n\
    }\n\
\n\
    location / {\n\
        try_files $uri $uri/ /index.html;\n\
    }\n\
}\n' > /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost:80/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
