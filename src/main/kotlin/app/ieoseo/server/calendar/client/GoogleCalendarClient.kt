package app.ieoseo.server.calendar.client

import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.calendar.domain.ExternalEvent
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
import java.time.format.DateTimeParseException

/**
 * Google Calendar API events.list 원시 호출 추상화 (이슈 #59).
 *
 * HTTP 실호출을 이 인터페이스 뒤로 둔다(oauth KakaoUserClient 패턴). 테스트는 가짜 주입.
 * 호출 실패(비2xx·전송 오류)는 [CalendarSyncException] 으로 던진다. 반환은 응답 JSON 본문.
 */
fun interface GoogleEventsApi {
    /** primary 캘린더의 [from]~[to] 구간 events.list 응답 본문(JSON). */
    fun listEvents(accessToken: String, from: LocalDate, to: LocalDate): String
}

/**
 * 실제 Google Calendar events.list 호출 — Bearer 토큰으로 primary 캘린더를 조회한다.
 *
 * `timeMin`/`timeMax`(RFC3339, 일자 자정 UTC 경계), `singleEvents=true` 로 반복 일정을 펼친다.
 * 키/토큰은 [CalendarConnection.accessToken] 로 주입(하드코딩 금지).
 */
@Component
class HttpGoogleEventsApi(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .build(),
) : GoogleEventsApi {

    override fun listEvents(accessToken: String, from: LocalDate, to: LocalDate): String {
        val timeMin = "${from}T00:00:00Z"
        val timeMax = "${to.plusDays(1)}T00:00:00Z"
        val uri = "$EVENTS_URI?singleEvents=true&orderBy=startTime" +
            "&timeMin=$timeMin&timeMax=$timeMax&maxResults=$MAX_RESULTS"
        return runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Authorization", "Bearer $accessToken")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw CalendarSyncException("Google 일정 조회 실패: HTTP ${response.statusCode()}")
            }
            response.body()
        }.getOrElse {
            if (it is CalendarSyncException) throw it
            throw CalendarSyncException("Google 일정 조회 중 오류", it)
        }
    }

    private companion object {
        const val EVENTS_URI = "https://www.googleapis.com/calendar/v3/calendars/primary/events"
        const val REQUEST_TIMEOUT_SECONDS = 5L
        const val MAX_RESULTS = 250
    }
}

/**
 * Google Calendar 동기화 클라이언트 (이슈 #59). events.list 응답을 [ExternalEvent] 로 매핑한다.
 *
 * 각 item 의 `id`(externalId), `summary`(title), `start.date`(종일)/`start.dateTime`(시각 포함)을
 * 사용한다. 날짜는 ISO ymd LocalDate, 시각은 `HH:mm`(종일이면 null). 토큰 미등록 시 즉시 실패.
 */
@Component
class GoogleCalendarClient(
    private val api: GoogleEventsApi,
    private val objectMapper: ObjectMapper,
) : CalendarClient {

    override val provider = CalendarProvider.GOOGLE

    override fun fetchEvents(connection: CalendarConnection, from: LocalDate, to: LocalDate): List<ExternalEvent> {
        val token = connection.accessToken
            ?: throw CalendarSyncException("Google access token 이 없습니다")
        val body = api.listEvents(token, from, to)
        val root = objectMapper.readTree(body)
        return root.path("items")
            .mapNotNull { toExternalEvent(connection, it) }
    }

    private fun toExternalEvent(connection: CalendarConnection, item: JsonNode): ExternalEvent? {
        val externalId = item.path("id").asString()
        if (externalId.isBlank()) return null
        val title = item.path("summary").asString().ifBlank { "(제목 없음)" }

        val start = item.path("start")
        val (date, time) = parseStart(start) ?: return null
        return ExternalEvent(
            userId = connection.userId,
            provider = CalendarProvider.GOOGLE,
            externalId = externalId,
            title = title,
            date = date,
            time = time,
        )
    }

    /** `start.dateTime`(시각 포함) 우선, 없으면 `start.date`(종일). 파싱 불가면 null. */
    private fun parseStart(start: JsonNode): Pair<LocalDate, String?>? {
        val dateTime = start.path("dateTime").asString()
        if (dateTime.isNotBlank()) {
            return runCatching {
                val odt = OffsetDateTime.parse(dateTime)
                odt.toLocalDate() to "%02d:%02d".format(odt.hour, odt.minute)
            }.getOrNull()
        }
        val date = start.path("date").asString()
        if (date.isNotBlank()) {
            return runCatching { LocalDate.parse(date) to null }
                .getOrElse { if (it is DateTimeParseException) null else throw it }
        }
        return null
    }
}
