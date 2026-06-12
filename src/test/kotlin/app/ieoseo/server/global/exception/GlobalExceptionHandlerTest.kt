package app.ieoseo.server.global.exception

import app.ieoseo.server.global.exception.NotFoundException
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [GlobalExceptionHandler] 단위 테스트. 미처리 예외는 500, 도메인 예외는 해당 4xx 로
 * 매핑되는지 검증한다.
 */
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `미처리 런타임 예외는 500 INTERNAL_ERROR 로 매핑된다`() {
        val response = handler.handleUnexpected(RuntimeException("boom"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.error?.code)
    }

    @Test
    fun `도메인 NotFound 예외는 404 로 매핑된다`() {
        val response = handler.handleNotFound(NotFoundException("task", 1))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("NOT_FOUND", response.body?.error?.code)
    }
}
