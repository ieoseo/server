package app.ieoseo.server.global.observability

/**
 * 미처리 예외를 외부 관측성(Sentry)으로 보고하는 포트.
 *
 * 구현은 DSN 설정 여부로 활성/비활성이 갈린다(미설정이면 no-op). 호출부는
 * 활성 여부를 신경 쓰지 않고 [captureException] 만 부른다.
 */
interface SentryReporter {
    /** DSN 이 설정돼 실제 전송이 이뤄지는지 여부. */
    val isEnabled: Boolean

    /** 예외를 Sentry 로 캡처한다. 비활성이면 아무 일도 하지 않는다. */
    fun captureException(throwable: Throwable)
}
