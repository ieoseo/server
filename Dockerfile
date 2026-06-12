# 이어서 server 컨테이너 이미지 (멀티스테이지). 배포 절차: docs 가이드/azure/배포.md (Azure Container Apps)
# 로컬: docker build -t ieoseo-server . && docker run -p 8080:8080 --env-file .env.local ieoseo-server

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

# 앱 포트 8080. 운영 프로파일(prod) 활성 — application-prod.yml 의 값은 컨테이너 환경변수(${ENV})로 주입.
EXPOSE 8080
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

# 헬스체크(컨테이너 레벨) — /health/check 는 공개(permitAll).
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/health/check || exit 1

# JAVA_OPTS 로 힙·GC 등 조정 가능. exec 로 PID 1 시그널 전달.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
