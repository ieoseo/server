package app.ieoseo.server.infrastructure.observability

import io.sentry.Sentry

/**
 * [SentryReporter] 기본 구현. [isEnabled] 가 false 면 아무것도 전송하지 않는다.
 *
 * [capture] 는 테스트 주입용 seam 이다(기본값은 Sentry 정적 호출). 운영에서는
 * [SentryConfig] 가 전역 [Sentry] 초기화 뒤 enabled=true 로 이 빈을 만든다.
 */
class SentryExceptionReporter(
    override val isEnabled: Boolean,
    private val capture: (Throwable) -> Unit = { Sentry.captureException(it) },
) : SentryReporter {
    override fun captureException(throwable: Throwable) {
        if (!isEnabled) return
        capture(throwable)
    }
}
