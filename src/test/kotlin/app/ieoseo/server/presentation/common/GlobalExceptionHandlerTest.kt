package app.ieoseo.server.presentation.common

import app.ieoseo.server.common.NotFoundException
import app.ieoseo.server.infrastructure.observability.SentryReporter
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [GlobalExceptionHandler] 단위 테스트. 미처리 예외만 Sentry 로 캡처되는지, 도메인 예외는
 * 캡처되지 않는지, 리포터 미주입(null)에서도 매핑이 동작하는지 검증한다.
 */
class GlobalExceptionHandlerTest {

    /** 캡처된 예외를 기록하는 테스트용 리포터. */
    private class RecordingReporter : SentryReporter {
        override val isEnabled: Boolean = true
        val captured = mutableListOf<Throwable>()

        override fun captureException(throwable: Throwable) {
            captured.add(throwable)
        }
    }

    @Test
    fun `미처리 런타임 예외는 500 INTERNAL_ERROR 로 매핑되고 Sentry 로 캡처된다`() {
        val reporter = RecordingReporter()
        val handler = GlobalExceptionHandler(reporter)
        val boom = RuntimeException("boom")

        val response = handler.handleUnexpected(boom)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.error?.code)
        assertEquals(listOf<Throwable>(boom), reporter.captured)
    }

    @Test
    fun `도메인 4xx 예외는 Sentry 로 캡처되지 않는다`() {
        val reporter = RecordingReporter()
        val handler = GlobalExceptionHandler(reporter)

        val response = handler.handleNotFound(NotFoundException("task", 1))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertTrue(reporter.captured.isEmpty())
    }

    @Test
    fun `SentryReporter 미주입(null)이어도 500 매핑은 동작한다`() {
        val handler = GlobalExceptionHandler(null)

        val response = handler.handleUnexpected(RuntimeException("x"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.error?.code)
    }
}
