package app.ieoseo.server.infrastructure.calendar

import app.ieoseo.server.domain.calendar.CalendarConnection
import app.ieoseo.server.domain.calendar.CalendarProvider
import app.ieoseo.server.domain.calendar.ExternalEvent
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Notion API 데이터베이스 query 원시 호출 추상화 (이슈 #59).
 *
 * HTTP 실호출을 이 인터페이스 뒤로 둔다(oauth KakaoUserClient 패턴). 테스트는 가짜 주입.
 * 호출 실패(비2xx·전송 오류)는 [CalendarSyncException] 으로 던진다. 반환은 응답 JSON 본문.
 *
 * 가져올 데이터베이스 id 는 연결 등록 시 [CalendarConnection.refreshToken] 에 보관한다
 * (Notion 은 refresh 개념이 없어 이 필드를 database id 보관에 재사용 — 본 트랙 단순화).
 */
fun interface NotionQueryApi {
    /** [databaseId] 데이터베이스의 [from]~[to] 구간 query 응답 본문(JSON). */
    fun queryDatabase(token: String, databaseId: String, from: LocalDate, to: LocalDate): String
}

/**
 * 실제 Notion 데이터베이스 query 호출 — integration 토큰으로 `databases/{id}/query` 를 POST 한다.
 *
 * `Date` 속성에 [from]~[to] 필터를 적용한다. `Notion-Version` 헤더 필수. 토큰/키는 연결에서 주입.
 */
@Component
class HttpNotionQueryApi(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .build(),
) : NotionQueryApi {

    override fun queryDatabase(token: String, databaseId: String, from: LocalDate, to: LocalDate): String {
        val payload = """
            {"filter":{"and":[
              {"property":"Date","date":{"on_or_after":"$from"}},
              {"property":"Date","date":{"on_or_before":"$to"}}
            ]},"page_size":$PAGE_SIZE}
        """.trimIndent()
        return runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URI/databases/$databaseId/query"))
                .header("Authorization", "Bearer $token")
                .header("Notion-Version", NOTION_VERSION)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw CalendarSyncException("Notion 일정 조회 실패: HTTP ${response.statusCode()}")
            }
            response.body()
        }.getOrElse {
            if (it is CalendarSyncException) throw it
            throw CalendarSyncException("Notion 일정 조회 중 오류", it)
        }
    }

    private companion object {
        const val BASE_URI = "https://api.notion.com/v1"
        const val NOTION_VERSION = "2022-06-28"
        const val REQUEST_TIMEOUT_SECONDS = 5L
        const val PAGE_SIZE = 100
    }
}

/**
 * Notion 동기화 클라이언트 (이슈 #59). 데이터베이스 query 응답을 [ExternalEvent] 로 매핑한다.
 *
 * 각 result 의 `id`(externalId), `properties.Name.title[0].plain_text`(title),
 * `properties.Date.date.start`(날짜/시각)을 사용한다. start 가 시각 포함이면 `HH:mm`, 아니면 종일.
 * database id 는 [CalendarConnection.refreshToken] 에 보관한다(본 트랙 단순화).
 */
@Component
class NotionCalendarClient(
    private val api: NotionQueryApi,
    private val objectMapper: ObjectMapper,
) : CalendarClient {

    override val provider = CalendarProvider.NOTION

    override fun fetchEvents(connection: CalendarConnection, from: LocalDate, to: LocalDate): List<ExternalEvent> {
        val token = connection.accessToken
            ?: throw CalendarSyncException("Notion 토큰이 없습니다")
        val databaseId = connection.refreshToken
            ?: throw CalendarSyncException("Notion 데이터베이스 id 가 없습니다")
        val body = api.queryDatabase(token, databaseId, from, to)
        val root = objectMapper.readTree(body)
        return root.path("results")
            .mapNotNull { toExternalEvent(connection, it) }
    }

    private fun toExternalEvent(connection: CalendarConnection, page: JsonNode): ExternalEvent? {
        val externalId = page.path("id").asString()
        if (externalId.isBlank()) return null

        val props = page.path("properties")
        val title = props.path("Name").path("title").firstOrNull()
            ?.path("plain_text")?.asString().orEmpty().ifBlank { "(제목 없음)" }

        val start = props.path("Date").path("date").path("start").asString()
        if (start.isBlank()) return null
        val (date, time) = parseStart(start) ?: return null

        return ExternalEvent(
            userId = connection.userId,
            provider = CalendarProvider.NOTION,
            externalId = externalId,
            title = title,
            date = date,
            time = time,
        )
    }

    /** ISO 날짜 또는 ISO 시각(`2026-06-04` / `2026-06-04T14:30:00+09:00`). 파싱 불가면 null. */
    private fun parseStart(start: String): Pair<LocalDate, String?>? {
        if (start.contains('T')) {
            return runCatching {
                val odt = OffsetDateTime.parse(start)
                odt.toLocalDate() to "%02d:%02d".format(odt.hour, odt.minute)
            }.getOrNull()
        }
        return runCatching { LocalDate.parse(start) to null as String? }.getOrNull()
    }
}
