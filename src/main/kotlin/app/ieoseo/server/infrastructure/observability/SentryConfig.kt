package app.ieoseo.server.infrastructure.observability

import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Sentry 코어 SDK 초기화 구성.
 *
 * Spring Boot auto-config 스타터 대신 코어 SDK(`io.sentry:sentry`)를 직접 init 한다
 * (SB4 호환·의존 최소화, ADR-0011). DSN 미설정 시 init 을 건너뛰고 no-op
 * [SentryReporter] 를 등록한다 → 로컬/CI 에서 외부 전송 0.
 */
@Configuration
@EnableConfigurationProperties(SentryProperties::class)
class SentryConfig {

    private val log = LoggerFactory.getLogger(SentryConfig::class.java)

    @Bean
    fun sentryReporter(properties: SentryProperties): SentryReporter {
        val enabled = properties.dsn.isNotBlank()
        if (enabled) {
            Sentry.init { options ->
                options.dsn = properties.dsn
                options.environment = properties.environment
                options.tracesSampleRate = properties.tracesSampleRate
                if (properties.release.isNotBlank()) {
                    options.release = properties.release
                }
            }
            log.info(
                "Sentry 활성화 (environment={}, tracesSampleRate={})",
                properties.environment,
                properties.tracesSampleRate,
            )
        } else {
            log.info("Sentry 비활성화 — SENTRY_DSN 미설정(외부 전송 없음)")
        }
        return SentryExceptionReporter(isEnabled = enabled)
    }
}
