# syntax=docker/dockerfile:1

# ---- build ----
FROM maven:3-eclipse-temurin-27 AS build
WORKDIR /build
# Dependências primeiro, para aproveitar o cache de camadas
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:25-jre-alpine-3.24
# Correções de segurança do Alpine publicadas depois da imagem base (gate 5: sem HIGH/CRITICAL)
RUN apk upgrade --no-cache
RUN addgroup -S -g 10001 appuser && adduser -S -u 10001 -G appuser appuser
WORKDIR /app
COPY --from=build --chown=appuser:appuser /build/target/app.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
