package app.ieoseo.server.calendar.client

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals

/**
 * HttpNotionQueryApi 의 Notion-Version 헤더 외부화(설정값) 검증.
 *
 * 로컬 스텁 HttpServer 를 띄워 실제로 전송된 `Notion-Version` 헤더를 캡처한다(외부 호출 0).
 * baseUri 는 생성자 인자로 스텁 서버를 가리키게 하고, api-version 은 주입값이 그대로 헤더로 나가는지 본다.
 */
class HttpNotionQueryApiTest {

    private lateinit var server: HttpServer
    private val captured = AtomicReference<String?>()
    private lateinit var baseUri: String

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1") { exchange ->
            captured.set(exchange.requestHeaders.getFirst("Notion-Version"))
            val body = """{"results":[]}"""
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        baseUri = "http://127.0.0.1:${server.address.port}/v1"
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `설정한 api-version 이 Notion-Version 헤더로 전송된다`() {
        val api = HttpNotionQueryApi(notionVersion = "2025-09-03", baseUri = baseUri)

        api.queryDatabase("token-placeholder", "db-placeholder", FROM, TO)

        assertEquals("2025-09-03", captured.get())
    }

    @Test
    fun `미설정 기본값은 2022-06-28 로 전송된다`() {
        val api = HttpNotionQueryApi(baseUri = baseUri)

        api.queryDatabase("token-placeholder", "db-placeholder", FROM, TO)

        assertEquals("2022-06-28", captured.get())
    }

    private companion object {
        val FROM: LocalDate = LocalDate.of(2026, 6, 1)
        val TO: LocalDate = LocalDate.of(2026, 6, 30)
    }
}
