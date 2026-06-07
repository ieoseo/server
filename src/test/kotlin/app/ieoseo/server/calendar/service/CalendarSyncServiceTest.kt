package app.ieoseo.server.calendar.service

import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.calendar.domain.ConnectionStatus
import app.ieoseo.server.calendar.domain.ExternalEvent
import app.ieoseo.server.calendar.client.CalendarClient
import app.ieoseo.server.calendar.client.CalendarClientRegistry
import app.ieoseo.server.calendar.client.CalendarSyncException
import app.ieoseo.server.calendar.repository.CalendarConnectionRepository
import app.ieoseo.server.calendar.repository.ExternalEventRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

/**
 * CalendarSyncService 단위 테스트 (이슈 #59) — 외부 HTTP 없이 **가짜 CalendarClient** 를 주입한다(CI 외부호출 0).
 *
 * upsert(신규 저장 / 기존 갱신)·중복 방지·실패 시 SYNC_FAILED 표기를 검증한다.
 * Repository 는 Mockito mock 으로 두고, 동기화 로직(분기·상태)만 단위로 본다.
 */
class CalendarSyncServiceTest {

    private val owner = UUID.randomUUID()
    private val today = LocalDate.of(2026, 6, 4)

    private val connectionRepository: CalendarConnectionRepository = mock(CalendarConnectionRepository::class.java)
    private val externalEventRepository: ExternalEventRepository = mock(ExternalEventRepository::class.java)

    private fun connection(provider: CalendarProvider) = CalendarConnection(
        userId = owner,
        provider = provider,
        accessToken = "token-placeholder",
        status = ConnectionStatus.CONNECTED,
    )

    private fun fakeClient(provider: CalendarProvider, events: List<ExternalEvent>) = object : CalendarClient {
        override val provider = provider
        override fun fetchEvents(connection: CalendarConnection, from: LocalDate, to: LocalDate) = events
    }

    private fun failingClient(provider: CalendarProvider) = object : CalendarClient {
        override val provider = provider
        override fun fetchEvents(connection: CalendarConnection, from: LocalDate, to: LocalDate): List<ExternalEvent> =
            throw CalendarSyncException("토큰 만료")
    }

    private fun ext(externalId: String, title: String) = ExternalEvent(
        userId = owner,
        provider = CalendarProvider.GOOGLE,
        externalId = externalId,
        title = title,
        date = today,
    )

    /** Kotlin non-null 파라미터용 Mockito any() 헬퍼(매처가 null 을 반환해 NPE 나는 문제 회피). */
    private fun anyExternal(): ExternalEvent {
        ArgumentMatchers.any(ExternalEvent::class.java)
        return ext("x", "x")
    }

    private fun service(registry: CalendarClientRegistry) =
        CalendarSyncService(connectionRepository, externalEventRepository, registry)

    /**
     * findByUserIdAndProviderAndExternalId 를 호출 인자 기반으로 응답하게 한다(매처 혼용 회피).
     * [existing] 에 든 externalId 는 기존(갱신 경로)으로, 나머지는 비어 있음(신규 저장 경로)으로 답한다.
     */
    private fun stubLookup(existing: Map<String, ExternalEvent> = emptyMap()) {
        `when`(
            externalEventRepository.findByUserIdAndProviderAndExternalId(
                anyUuid(),
                anyProvider(),
                ArgumentMatchers.anyString(),
            ),
        ).thenAnswer { invocation ->
            val externalId = invocation.arguments[2] as String
            Optional.ofNullable(existing[externalId])
        }
        `when`(externalEventRepository.save(anyExternal())).thenAnswer { it.arguments[0] }
    }

    private fun anyUuid(): UUID {
        ArgumentMatchers.any(UUID::class.java)
        return owner
    }

    private fun anyProvider(): CalendarProvider {
        ArgumentMatchers.any(CalendarProvider::class.java)
        return CalendarProvider.GOOGLE
    }

    @Test
    fun `동기화는 가져온 외부 일정을 새로 저장한다`() {
        val conn = connection(CalendarProvider.GOOGLE)
        `when`(connectionRepository.findAllByUserId(owner)).thenReturn(listOf(conn))
        stubLookup() // 모두 신규

        val registry = CalendarClientRegistry(
            listOf(fakeClient(CalendarProvider.GOOGLE, listOf(ext("g1", "회의"), ext("g2", "점심")))),
        )

        val results = service(registry).syncAll(owner, today)

        assertEquals(1, results.size)
        assertEquals(2, results[0].imported)
        assertEquals(ConnectionStatus.CONNECTED, conn.status)
        verify(externalEventRepository, org.mockito.Mockito.times(2)).save(anyExternal())
    }

    @Test
    fun `재동기화는 같은 externalId 를 중복 저장하지 않고 갱신한다`() {
        val conn = connection(CalendarProvider.GOOGLE)
        val existing = ext("g1", "옛 제목")
        `when`(connectionRepository.findAllByUserId(owner)).thenReturn(listOf(conn))
        stubLookup(mapOf("g1" to existing))

        val registry = CalendarClientRegistry(
            listOf(fakeClient(CalendarProvider.GOOGLE, listOf(ext("g1", "새 제목")))),
        )

        service(registry).syncAll(owner, today)

        // 신규 save 호출 없음(갱신 경로) — 중복 방지.
        verify(externalEventRepository, never()).save(anyExternal())
        assertEquals("새 제목", existing.title) // 기존 엔티티가 갱신됨
    }

    @Test
    fun `provider 실패는 연결을 SYNC_FAILED 로 표기하고 저장하지 않는다`() {
        val conn = connection(CalendarProvider.GOOGLE)
        `when`(connectionRepository.findAllByUserId(owner)).thenReturn(listOf(conn))
        val registry = CalendarClientRegistry(listOf(failingClient(CalendarProvider.GOOGLE)))

        val results = service(registry).syncAll(owner, today)

        assertEquals(0, results[0].imported)
        assertEquals(ConnectionStatus.SYNC_FAILED, conn.status)
        verify(externalEventRepository, never()).save(anyExternal())
    }
}
