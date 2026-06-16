package app.ieoseo.server.calendar.client

import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.calendar.domain.ExternalEvent
import app.ieoseo.server.calendar.oauth.GoogleOAuthClient
import app.ieoseo.server.calendar.oauth.GoogleOAuthException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Google Calendar API events.list 원시 호출 추상화 (이슈 #59).
 *
 * HTTP 실호출을 이 인터페이스 뒤로 둔다(fun interface 라 테스트는 가짜 람다 주입).
 * 한 페이지 응답 본문(JSON)을 반환한다 — 페이지네이션은 [GoogleCalendarClient] 가 [pageToken] 으로 잇는다.
 * 비2xx·전송 오류는 [CalendarSyncException], 특히 **401 은 [CalendarAuthExpiredException]**(토큰 갱신 신호).
 */
fun interface GoogleEventsApi {
    /** primary 캘린더의 [from]~[to] 구간 events.list 응답 본문(JSON). [pageToken] 이 있으면 그 페이지부터. */
    fun listEvents(accessToken: String, from: LocalDate, to: LocalDate, pageToken: String?): String
}

/**
 * 실제 Google Calendar events.list 호출 — Bearer 토큰으로 primary 캘린더를 조회한다.
 *
 * `timeMin`/`timeMax`(RFC3339, 일자 자정 UTC 경계), `singleEvents=true` 로 반복 일정을 펼친다.
 * 키/토큰은 [CalendarConnection.accessToken] 로 주입(하드코딩 금지). 401 은 만료 신호로 구분한다.
 */
@Component
class HttpGoogleEventsApi(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .build(),
) : GoogleEventsApi {

    override fun listEvents(accessToken: String, from: LocalDate, to: LocalDate, pageToken: String?): String {
        val timeMin = "${from}T00:00:00Z"
        val timeMax = "${to.plusDays(1)}T00:00:00Z"
        val pageParam = pageToken?.let { "&pageToken=${enc(it)}" } ?: ""
        val uri = "$EVENTS_URI?singleEvents=true&orderBy=startTime" +
            "&timeMin=$timeMin&timeMax=$timeMax&maxResults=$MAX_RESULTS$pageParam"
        return runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Authorization", "Bearer $accessToken")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val status = response.statusCode()
            if (status == 401) {
                throw CalendarAuthExpiredException("Google access token 만료/무효(HTTP 401)")
            }
            if (status !in 200..299) {
                throw CalendarSyncException("Google 일정 조회 실패: HTTP $status")
            }
            response.body()
        }.getOrElse {
            if (it is CalendarSyncException) throw it
            throw CalendarSyncException("Google 일정 조회 중 오류", it)
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

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
 *
 * 견고화(B-3):
 * - **페이지네이션**: `nextPageToken` 을 따라 전 페이지를 모은다([MAX_PAGES] 안전 상한, 초과 시 경고).
 * - **토큰 갱신**: 401([CalendarAuthExpiredException]) 이면 refresh token 으로 access token 을
 *   재발급해 연결에 반영하고 **1회 재시도**한다. refresh token 이 없거나 재시도도 실패하면 그대로
 *   [CalendarSyncException] 으로 전파(연결 SYNC_FAILED).
 */
@Component
class GoogleCalendarClient(
    private val api: GoogleEventsApi,
    private val objectMapper: ObjectMapper,
    private val oauthClient: GoogleOAuthClient,
) : CalendarClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override val provider = CalendarProvider.GOOGLE

    override fun fetchEvents(connection: CalendarConnection, from: LocalDate, to: LocalDate): List<ExternalEvent> {
        val token = connection.accessToken
            ?: throw CalendarSyncException("Google access token 이 없습니다")
        return try {
            fetchAllPages(connection, token, from, to)
        } catch (expired: CalendarAuthExpiredException) {
            // 만료 → refresh 가능하면 새 토큰으로 1회만 재시도(다시 401 이면 전파 → SYNC_FAILED).
            val refreshed = refreshAccessToken(connection) ?: throw expired
            log.info("Google access token 갱신 후 재시도(user={})", connection.userId)
            fetchAllPages(connection, refreshed, from, to)
        }
    }

    /** `nextPageToken` 을 따라 전 페이지의 일정을 모은다. 안전 상한 초과분은 경고하고 생략한다. */
    private fun fetchAllPages(
        connection: CalendarConnection,
        accessToken: String,
        from: LocalDate,
        to: LocalDate,
    ): List<ExternalEvent> {
        val events = mutableListOf<ExternalEvent>()
        var pageToken: String? = null
        var pages = 0
        do {
            val root = objectMapper.readTree(api.listEvents(accessToken, from, to, pageToken))
            root.path("items").mapNotNullTo(events) { toExternalEvent(connection, it) }
            pageToken = root.path("nextPageToken").asString().ifBlank { null }
            pages++
            if (pages >= MAX_PAGES && pageToken != null) {
                log.warn(
                    "Google 일정 페이지 상한({}) 도달 — 이후 페이지는 이번 동기화에서 생략(user={})",
                    MAX_PAGES,
                    connection.userId,
                )
                break
            }
        } while (pageToken != null)
        return events
    }

    /**
     * refresh token 으로 access token 을 재발급해 [connection] 에 반영한다(트랜잭션 내 dirty checking 으로 영속).
     * refresh token 이 없으면 null, 갱신 호출 실패는 [CalendarSyncException] 으로 던진다.
     */
    private fun refreshAccessToken(connection: CalendarConnection): String? {
        val refreshToken = connection.refreshToken ?: return null
        val tokens = try {
            oauthClient.refresh(refreshToken)
        } catch (e: GoogleOAuthException) {
            throw CalendarSyncException("Google access token 갱신 실패", e)
        }
        connection.accessToken = tokens.accessToken
        connection.expiresAt = tokens.expiresAt
        tokens.refreshToken?.let { connection.refreshToken = it } // 보통 유지되나 회전되면 갱신
        return tokens.accessToken
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
                // 절대 시각을 표시 타임존(KST)으로 환산 — UTC 그대로 쓰면 날짜·시각이 어긋난다.
                OffsetDateTime.parse(dateTime).toDisplayDateAndTime()
            }.getOrNull()
        }
        val date = start.path("date").asString()
        if (date.isNotBlank()) {
            return runCatching { LocalDate.parse(date) to null }
                .getOrElse { if (it is DateTimeParseException) null else throw it }
        }
        return null
    }

    private companion object {
        /** 페이지네이션 안전 상한(페이지). 250건/페이지 × 20 = 5000건까지 모은다. */
        const val MAX_PAGES = 20
    }
}
