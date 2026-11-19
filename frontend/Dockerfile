# syntax=docker/dockerfile:1

# ---- build ----
FROM node:24.22.3-alpine3.24 AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- runtime: nginx sem root, porta 8080 ----
FROM nginxinc/nginx-unprivileged:1.30.6-alpine3.24
# Correções de segurança do Alpine publicadas depois da imagem base (gate 5: sem HIGH/CRITICAL)
# O nginx 1.30.7 (18/11/2026, CVE-2026-123007 e outros) ainda não tem imagem nginx-unprivileged, e a
# base fixa o pacote em 1.30.6: atualiza pelo repositório assinado do nginx.org (a chave já vem na
# imagem). Remover o `apk add` quando sair a nginxinc/nginx-unprivileged:1.30.7-alpine3.24.
USER root
RUN apk upgrade --no-cache \
 && apk add --no-cache --repository https://nginx.org/packages/alpine/v3.24/main \
      nginx=1.30.7-r1 nginx-module-geoip nginx-module-image-filter nginx-module-njs nginx-module-xslt
USER 101
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1
