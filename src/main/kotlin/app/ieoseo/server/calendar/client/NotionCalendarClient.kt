package app.ieoseo.server.calendar.client

import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.calendar.domain.ExternalEvent
import org.springframework.beans.factory.annotation.Value
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
 * HTTP 실호출을 이 인터페이스 뒤로 둔다(fun interface 라 테스트는 가짜 람다 주입).
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
 *
 * `Notion-Version` 은 날짜 고정 API 버전이라 시간이 지나면 갱신해야 한다. 하드코딩 대신
 * `ieoseo.calendar.notion.api-version`(application.yml, 기본 2022-06-28) 으로 외부화한다.
 * [baseUri] 도 테스트에서 로컬 스텁 서버로 덮어쓸 수 있도록 생성자 인자로 둔다.
 */
@Component
class HttpNotionQueryApi(
    @Value("\${ieoseo.calendar.notion.api-version:$DEFAULT_NOTION_VERSION}")
    private val notionVersion: String = DEFAULT_NOTION_VERSION,
    private val baseUri: String = DEFAULT_BASE_URI,
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
                .uri(URI.create("$baseUri/databases/$databaseId/query"))
                .header("Authorization", "Bearer $token")
                .header("Notion-Version", notionVersion)
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
        const val DEFAULT_BASE_URI = "https://api.notion.com/v1"

        /**
         * Notion API 버전 기본값(미설정 시 fallback). 실제 값은 `ieoseo.calendar.notion.api-version`
         * (application.yml) 으로 주입한다. 둘은 동일 기본값을 유지해야 한다.
         */
        const val DEFAULT_NOTION_VERSION = "2022-06-28"
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
                // 절대 시각을 표시 타임존(KST)으로 환산 — UTC 그대로 쓰면 날짜·시각이 어긋난다.
                OffsetDateTime.parse(start).toDisplayDateAndTime()
            }.getOrNull()
        }
        return runCatching { LocalDate.parse(start) to null as String? }.getOrNull()
    }
}
