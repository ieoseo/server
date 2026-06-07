package app.ieoseo.server.calendar.client

import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * provider 별 CalendarClient 매핑 단위 테스트 — HTTP 실호출 없이 추상화 뒤 가짜 응답을 주입한다(CI 외부호출 0).
 *
 * Google/Notion: 가짜 API 가 JSON 본문을 돌려주고, ExternalEvent 매핑(날짜/시각/제목/externalId)을 확인한다.
 * Apple: 스텁이라 항상 빈 결과. 토큰 미등록은 CalendarSyncException.
 */
class CalendarClientTest {

    private val mapper = JsonMapper.builder().build()
    private val userId = UUID.randomUUID()
    private val from = LocalDate.of(2026, 6, 1)
    private val to = LocalDate.of(2026, 6, 30)

    private fun connection(provider: CalendarProvider, token: String? = "token-placeholder", db: String? = null) =
        CalendarConnection(userId = userId, provider = provider, accessToken = token, refreshToken = db)

    // ---- Google ----

    @Test
    fun `Google 클라이언트는 dateTime 일정을 날짜와 HH-mm 으로 매핑한다`() {
        val body = """
            {"items":[
              {"id":"g1","summary":"회의","start":{"dateTime":"2026-06-04T14:30:00+09:00"}},
              {"id":"g2","summary":"휴가","start":{"date":"2026-06-10"}}
            ]}
        """.trimIndent()
        val client = GoogleCalendarClient({ _, _, _ -> body }, mapper)

        val events = client.fetchEvents(connection(CalendarProvider.GOOGLE), from, to)

        assertEquals(2, events.size)
        assertEquals("g1", events[0].externalId)
        assertEquals(LocalDate.of(2026, 6, 4), events[0].date)
        assertEquals("14:30", events[0].time)
        assertEquals(LocalDate.of(2026, 6, 10), events[1].date)
        assertNull(events[1].time) // 종일
    }

    @Test
    fun `Google 클라이언트는 토큰이 없으면 CalendarSyncException`() {
        val client = GoogleCalendarClient({ _, _, _ -> "{}" }, mapper)
        assertThrows<CalendarSyncException> {
            client.fetchEvents(connection(CalendarProvider.GOOGLE, token = null), from, to)
        }
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
