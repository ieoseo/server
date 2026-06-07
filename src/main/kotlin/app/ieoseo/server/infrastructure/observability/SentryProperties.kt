package app.ieoseo.server.infrastructure.observability

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 관측성(Sentry) 설정. 값은 `application.yaml`/환경변수에서 주입한다(시크릿 하드코딩 금지).
 *
 * 계약: ADR-0011, `센트리-가이드.md`.
 * - [dsn]: Sentry 프로젝트 DSN(`SENTRY_DSN`). 빈 값이면 Sentry 비활성(외부 전송 0).
 * - [environment]: 환경 태그(`SENTRY_ENVIRONMENT`, 예: local/staging/production).
 * - [release]: 릴리스 식별자(`SENTRY_RELEASE`, 선택). 비면 미설정.
 * - [tracesSampleRate]: 성능 트레이스 샘플링 비율(`SENTRY_TRACES_SAMPLE_RATE`, 기본 0.0 = 에러만).
 */
@ConfigurationProperties(prefix = "ieoseo.sentry")
data class SentryProperties(
    val dsn: String = "",
    val environment: String = "local",
    val release: String = "",
    val tracesSampleRate: Double = 0.0,
)
