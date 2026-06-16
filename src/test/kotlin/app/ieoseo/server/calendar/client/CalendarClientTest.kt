package app.ieoseo.server.calendar.client

import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.calendar.oauth.GoogleOAuthClient
import app.ieoseo.server.calendar.oauth.GoogleTokens
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * provider 별 CalendarClient 매핑 단위 테스트 — HTTP 실호출 없이 추상화 뒤 가짜 응답을 주입한다(CI 외부호출 0).
 *
 * Google/Notion: 가짜 API 가 JSON 본문을 돌려주고, ExternalEvent 매핑(날짜/시각/제목/externalId)을 확인한다.
 * Apple: 스텁이라 항상 빈 결과. 토큰 미등록은 CalendarSyncException.
 * Google 견고화(B-3): 페이지네이션·401 토큰 갱신 재시도도 검증한다.
 */
class CalendarClientTest {

    private val mapper = JsonMapper.builder().build()
    private val userId = UUID.randomUUID()
    private val from = LocalDate.of(2026, 6, 1)
    private val to = LocalDate.of(2026, 6, 30)

    private fun connection(provider: CalendarProvider, token: String? = "token-placeholder", db: String? = null) =
        CalendarConnection(userId = userId, provider = provider, accessToken = token, refreshToken = db)

    /** 갱신을 쓰지 않는(호출되면 실패) 가짜 OAuth 클라이언트. */
    private fun unusedOAuth(): GoogleOAuthClient = object : GoogleOAuthClient {
        override fun exchangeCode(code: String): GoogleTokens = error("exchangeCode 는 호출되지 않아야 한다")
        override fun refresh(refreshToken: String): GoogleTokens = error("refresh 는 호출되지 않아야 한다")
    }

    /** 새 access token 으로 갱신하는 가짜 OAuth 클라이언트. */
    private fun refreshingOAuth(newToken: String): GoogleOAuthClient = object : GoogleOAuthClient {
        override fun exchangeCode(code: String): GoogleTokens = error("exchangeCode 는 호출되지 않아야 한다")
        override fun refresh(refreshToken: String): GoogleTokens =
            GoogleTokens(accessToken = newToken, refreshToken = refreshToken, expiresAt = Instant.now().plusSeconds(3600))
    }

    private fun googleClient(oauth: GoogleOAuthClient = unusedOAuth(), api: GoogleEventsApi) =
        GoogleCalendarClient(api, mapper, oauth)

    // ---- Google ----

    @Test
    fun `Google 클라이언트는 dateTime 일정을 날짜와 HH-mm 으로 매핑한다`() {
        val body = """
            {"items":[
              {"id":"g1","summary":"회의","start":{"dateTime":"2026-06-04T14:30:00+09:00"}},
              {"id":"g2","summary":"휴가","start":{"date":"2026-06-10"}}
            ]}
        """.trimIndent()
        val client = googleClient(api = { _, _, _, _ -> body })

        val events = client.fetchEvents(connection(CalendarProvider.GOOGLE), from, to)

        assertEquals(2, events.size)
        assertEquals("g1", events[0].externalId)
        assertEquals(LocalDate.of(2026, 6, 4), events[0].date)
        assertEquals("14:30", events[0].time)
        assertEquals(LocalDate.of(2026, 6, 10), events[1].date)
        assertNull(events[1].time) // 종일
    }

    @Test
    fun `Google 클라이언트는 UTC 시각을 KST(+9)로 환산해 날짜·시각을 맞춘다`() {
        // 2026-06-16T23:00:00Z == 2026-06-17 08:00 KST. UTC 그대로 쓰면 6/16 23:00 으로 어긋난다.
        val body = """
            {"items":[{"id":"g3","summary":"아침 미팅","start":{"dateTime":"2026-06-16T23:00:00Z"}}]}
        """.trimIndent()
        val client = googleClient(api = { _, _, _, _ -> body })

        val events = client.fetchEvents(connection(CalendarProvider.GOOGLE), from, to)

        assertEquals(LocalDate.of(2026, 6, 17), events[0].date)
        assertEquals("08:00", events[0].time)
    }

    @Test
    fun `Google 클라이언트는 토큰이 없으면 CalendarSyncException`() {
        val client = googleClient(api = { _, _, _, _ -> "{}" })
        assertThrows<CalendarSyncException> {
            client.fetchEvents(connection(CalendarProvider.GOOGLE, token = null), from, to)
        }
    }

    @Test
    fun `Google 클라이언트는 nextPageToken 을 따라 여러 페이지를 모은다`() {
        val page1 = """{"items":[{"id":"g1","summary":"A","start":{"date":"2026-06-04"}}],"nextPageToken":"p2"}"""
        val page2 = """{"items":[{"id":"g2","summary":"B","start":{"date":"2026-06-05"}}]}"""
        val client = googleClient(api = { _, _, _, pageToken -> if (pageToken == null) page1 else page2 })

        val events = client.fetchEvents(connection(CalendarProvider.GOOGLE), from, to)

        assertEquals(2, events.size)
        assertEquals(listOf("g1", "g2"), events.map { it.externalId })
    }

    @Test
    fun `Google 클라이언트는 401 이면 토큰을 갱신하고 재시도한다`() {
        val body = """{"items":[{"id":"g1","summary":"A","start":{"date":"2026-06-04"}}]}"""
        // 옛 토큰이면 401, 새 토큰이면 정상 응답.
        val api = GoogleEventsApi { accessToken, _, _, _ ->
            if (accessToken == "old-token") throw CalendarAuthExpiredException("401") else body
        }
        val conn = connection(CalendarProvider.GOOGLE, token = "old-token", db = "refresh-token")
        val client = googleClient(oauth = refreshingOAuth("new-token"), api = api)

        val events = client.fetchEvents(conn, from, to)

        assertEquals(1, events.size)
        assertEquals("new-token", conn.accessToken) // 갱신 반영
    }

    @Test
    fun `Google 클라이언트는 refresh token 이 없으면 401 을 그대로 전파한다`() {
        val api = GoogleEventsApi { _, _, _, _ -> throw CalendarAuthExpiredException("401") }
        val conn = connection(CalendarProvider.GOOGLE, token = "old-token", db = null) // refreshToken 없음
        val client = googleClient(api = api)

        assertThrows<CalendarSyncException> { client.fetchEvents(conn, from, to) }
    }

    // ---- Notion ----

    @Test
    fun `Notion 클라이언트는 results 의 Name 과 Date 를 매핑한다`() {
        val body = """
            {"results":[
              {"id":"n1","properties":{
                "Name":{"title":[{"plain_text":"디자인 리뷰"}]},
                "Date":{"date":{"start":"2026-06-04T14:30:00+09:00"}}
              }}
            ]}
        """.trimIndent()
        val client = NotionCalendarClient({ _, _, _, _ -> body }, mapper)

        val events = client.fetchEvents(
            connection(CalendarProvider.NOTION, db = "database-id-placeholder"),
            from,
            to,
        )

        assertEquals(1, events.size)
        assertEquals("n1", events[0].externalId)
        assertEquals("디자인 리뷰", events[0].title)
        assertEquals("14:30", events[0].time)
    }

    @Test
    fun `Notion 클라이언트는 database id 가 없으면 CalendarSyncException`() {
        val client = NotionCalendarClient({ _, _, _, _ -> "{}" }, mapper)
        assertThrows<CalendarSyncException> {
            client.fetchEvents(connection(CalendarProvider.NOTION, db = null), from, to)
        }
    }

    // ---- Apple (스텁) ----

    @Test
    fun `Apple 클라이언트는 미지원이라 항상 빈 결과`() {
        val events = AppleCalendarClient().fetchEvents(connection(CalendarProvider.APPLE), from, to)
        assertEquals(0, events.size)
    }

    // ---- Registry ----

    @Test
    fun `registry 는 등록되지 않은 provider 를 CalendarSyncException 으로 거부한다`() {
        val registry = CalendarClientRegistry(listOf(AppleCalendarClient()))
        assertThrows<CalendarSyncException> { registry.forProvider(CalendarProvider.GOOGLE) }
    }
}
