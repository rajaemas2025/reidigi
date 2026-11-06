# =============================================================================
# REIDIG Operational Dashboard (React 19 + Vite + TypeScript -> Nginx SPA)
# Mendukung Build Context dari root repo (`.`) maupun subfolder (`./dashboard`)
# =============================================================================
FROM node:22-alpine AS builder

WORKDIR /app

COPY . /tmp/build-ctx
RUN if [ -f /tmp/build-ctx/dashboard/package.json ]; then \
        cp -r /tmp/build-ctx/dashboard/. /app/; \
    else \
        cp -r /tmp/build-ctx/. /app/; \
    fi && rm -rf /tmp/build-ctx

RUN npm ci || npm install
RUN npm run build

# Stage 2: Production Nginx Server
FROM nginx:stable-alpine

COPY --from=builder /app/dist /usr/share/nginx/html

# Konfigurasi Nginx untuk React Router SPA (fallback ke index.html) & endpoint /health
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
    location / {\n\
        try_files $uri $uri/ /index.html;\n\
    }\n\
\n\
    location ~* \\.(js|css|png|jpg|jpeg|gif|ico|svg|woff2?)$ {\n\
        expires 30d;\n\
        add_header Cache-Control "public, immutable";\n\
    }\n\
}\n' > /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget -qO- http://localhost:80/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
