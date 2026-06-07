package app.ieoseo.server.infrastructure.observability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SentryExceptionReporter] 단위 테스트. 외부 전송 없이 capture seam 으로 동작을 검증한다.
 */
class SentryExceptionReporterTest {

    @Test
    fun `비활성(disabled)이면 예외를 캡처하지 않는다`() {
        val captured = mutableListOf<Throwable>()
        val reporter = SentryExceptionReporter(isEnabled = false, capture = { captured.add(it) })

        reporter.captureException(RuntimeException("무시되어야 함"))

        assertFalse(reporter.isEnabled)
        assertTrue(captured.isEmpty())
    }

    @Test
    fun `활성(enabled)이면 주어진 예외를 그대로 캡처한다`() {
        val captured = mutableListOf<Throwable>()
        val reporter = SentryExceptionReporter(isEnabled = true, capture = { captured.add(it) })
        val boom = IllegalStateException("boom")

        reporter.captureException(boom)

        assertTrue(reporter.isEnabled)
        assertEquals(listOf<Throwable>(boom), captured)
    }
}
