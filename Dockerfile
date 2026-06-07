# 이어서 server 컨테이너 이미지 (멀티스테이지).
# 빌드: docker build -t ieoseo-server ./server
# 실행: docker run -p 8080:8080 --env-file server/.env ieoseo-server
# Azure App Service(컨테이너) 배포 대상 — 자세한 절차: docs/03-개발프로세스/배포-Azure.md

# ── 1) 빌드 스테이지 — Gradle wrapper 로 부트 JAR 생성 ──
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar -x test

# ── 2) 런타임 스테이지 — JRE 만(경량) ──
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# 비루트 사용자로 실행(보안).
RUN addgroup --system app && adduser --system --ingroup app app
COPY --from=build /app/build/libs/*.jar app.jar
USER app

# 앱 포트(application.yaml 의 SERVER_PORT 기본 8080). Azure App Service 는 WEBSITES_PORT=8080 설정.
EXPOSE 8080
ENV SERVER_PORT=8080

# 헬스체크(컨테이너 레벨) — /health/check 는 공개(permitAll).
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/health/check || exit 1

# JAVA_OPTS 로 힙·GC 등 조정 가능. exec 로 PID 1 시그널 전달.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
