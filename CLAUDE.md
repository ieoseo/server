# server/ — 이어서 Spring Boot API

이어서의 백엔드. 인증·영속화·도메인 규칙(권위)·외부 동기화·알림 담당. 클라이언트(`../client`)와는 HTTP API로만 연결.

## ⚠️ 변경 시 docs 동기화 (MUST)
- API 엔드포인트/요청·응답 스키마 변경 → `../docs/05-API/` (없으면 생성) 갱신.
- 도메인 모델/규칙 변경 → `../docs/06-백엔드/` (DDD, 없으면 생성) 갱신.
- 스택/의존성/버전 변경 → `../docs/02-아키텍처/기술스택.md` 갱신.
- 인증·구조 등 되돌리기 어려운 결정 → `../docs/04-ADR/` 새 ADR.
- 문서 안 고친 변경은 미완성.

## 스택 (ADR-0002)
- **Kotlin 2.2.21 + Spring Boot 4.0.6 + Java 21**, Gradle Kotlin DSL.
- Web: `spring-boot-starter-webmvc` (REST)
- 영속화: `spring-boot-starter-data-jpa` + **PostgreSQL**
- 캐시: 현재 미사용(Redis 의존성 제거됨). 필요 시 재도입.
- 직렬화: `jackson-module-kotlin`, `kotlin-reflect`
- 패키지 루트: `app.ieoseo.server`

## TDD (MUST)
- **테스트 먼저**: RED→GREEN→REFACTOR, 커버리지 80%+. [../docs/03-개발프로세스/TDD-가이드.md](../docs/03-개발프로세스/TDD-가이드.md).
- 단위(도메인 불변식·상태머신·자동이월·Service) + 슬라이스(`@WebMvcTest`, `@DataJpaTest`). 통합은 Testcontainers(추후).
- 도메인 규칙은 우선적으로 높은 커버리지. 테스트와 구현은 같은 PR.

## 패키지 레이아웃 — 레이어드 (ADR-0009)
루트 `app.ieoseo.server` 아래 **layer-first**, 각 레이어 하위에 **feature 서브패키지**(`auth`·`event`·`task`·`debt`·`notification`).

```
presentation/{feature}/   @RestController · 요청/응답 DTO   (공통: presentation/common — GlobalExceptionHandler·HealthCheckController)
application/{feature}/     @Service (유스케이스 오케스트레이션)
domain/{feature}/          @Entity · 값객체 · enum · 순수 도메인 서비스   (공통 베이스: domain/common — BaseEntity)
infrastructure/            persistence/{feature}(JpaRepository) · security(JWT·Filter·SecurityConfig) · oauth · auth(RefreshTokenStore 구현) · config
common/                    ApiResponse·ApiError·공통 예외
ServerApplication.kt       루트 @SpringBootApplication (전 하위 패키지 자동 스캔)
```

- **의존 방향(강제)**: `presentation → application → domain`, `infrastructure → domain`. `domain`은 다른 레이어 의존 금지(Spring/JPA 제외).
- 새 클래스는 위 규칙대로 배치한다. 보안 원시 타입(AuthPrincipal·TokenType 등)은 `infrastructure/security`에 둔다.

## 코드 컨벤션
- **레이어드**: `presentation`(controller·DTO) → `application`(@Service, 도메인 규칙 오케스트레이션) → `domain`(규칙·엔티티) / `infrastructure`(JPA·security·oauth). controller는 얇게.
- 엔티티는 `allOpen`(@Entity/@MappedSuperclass/@Embeddable) 대상 — JPA 프록시. 생성자/검증으로 불변식 보호.
- null-safety: 플랫폼 타입 주의(`-Xjsr305=strict`). nullable은 명시적으로.
- DTO와 엔티티 분리. API 응답은 공통 envelope(success/data/error/meta) 지향.
- 도메인 규칙(시간부채 자동 이월 우선순위 등)은 **server가 권위** — 클라이언트 계산을 신뢰하지 않는다.
- 입력 검증은 경계(controller/DTO)에서. 비밀값은 환경변수/시크릿(하드코딩 금지).

## 보안 (구현됨)
- 자체 Spring Security(stateless) + JWT(access/refresh, Redis 회전·in-memory 폴백) + 소셜 OAuth(Google/Apple/Kakao) — [ADR-0008](../docs/04-ADR/0008-인증-자체JWT-OAuth-채택.md). events/tasks/debts/notifications는 인증 필수(user_id 스코프).

## 환경변수 / 실행
- 설정값은 `application.yml`의 `${...}` 플레이스홀더 → **프로파일별 env 파일**에서 주입: local=`server/.env.local`, prod=`server/.env.prod`(각 `application-{profile}.yml`의 `spring.config.import: optional:file:.env.local[.properties]`). CI/운영은 실제 OS 환경변수로 덮어씀.
- 최초 1회: `cp .env.example .env.local` (server/ 에서). `.env.example`은 **키만**(값/주석 없음), 실값은 `.env.local`/`.env.prod`(gitignore)에만. **값 예시는 [../docs/가이드/환경변수.md](../docs/가이드/환경변수.md)**.
- **로컬 DB(PostgreSQL)**: `docker compose --env-file .env.local up -d db` (compose 는 기본 `.env` 만 자동 로드하므로 `.env.local` 을 명시; Spring 과 같은 파일 공유). 종료 `docker compose --env-file .env.local down`(데이터까지 `-v`). 값 누락 시 `${VAR:?}` 로 즉시 에러.
- 빌드/실행: `./gradlew bootRun` (DB 떠 있어야 정상 기동; `ddl-auto: validate` + Flyway 스키마 전제).
- 테스트: `./gradlew test` (외부 의존 없이 슬라이스/H2). DB 불필요.
- **로깅(이슈 #5)**: `application.yml` 기본 `app.ieoseo.server=INFO`. local 은 `app.ieoseo.server`·Spring Security·web `DEBUG`(진단), prod 는 INFO(보안/web WARN). 운영에서 일시 상향은 `LOG_LEVEL_APP=DEBUG` 환경변수. 4xx 도메인 예외(400/409)는 `GlobalExceptionHandler` 가 WARN 으로, 미처리 5xx 는 ERROR+Sentry 로 남긴다. Azure 는 App Service → Log Stream 으로 확인. 토큰·비밀은 로깅 금지.
- **Google 캘린더 OAuth(서버 주도, 이슈 #9, Phase A)**: `calendar/oauth/` — `GET /api/v1/calendar/connect/google/url`(인증, 동의 URL 발급) → 앱이 외부 브라우저로 염 → Google 콜백 `GET /api/v1/calendar/oauth/google/callback`(**공개**, SecurityConfig permitAll) → 토큰 교환·`CalendarConnection` 저장 → 앱 딥링크(`app.ieoseo://calendar-callback?status=...`) 302 복귀. 사용자 식별은 `OAuthStateStore`(일회용 state, 인메모리). 설정: `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`·`GOOGLE_CALENDAR_REDIRECT_URI`·`GOOGLE_CALENDAR_RETURN_DEEPLINK`(env, 미설정 시 비활성). 전 실패 지점 로깅(토큰 제외). **선행: Google Cloud Calendar API 활성화 + 동의화면 scope + redirect URI 등록**. **후속: 토큰 refresh-on-expiry, 클라이언트 배선(Phase B)**.
