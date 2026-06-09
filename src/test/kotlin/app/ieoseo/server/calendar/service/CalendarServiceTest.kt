package app.ieoseo.server.calendar.service

import app.ieoseo.server.global.exception.NotFoundException
import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.calendar.domain.ConnectionStatus
import app.ieoseo.server.calendar.repository.CalendarConnectionRepository
import app.ieoseo.server.calendar.repository.ExternalEventRepository
import app.ieoseo.server.calendar.dto.ConnectCalendarRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

/**
 * CalendarService 연결 CRUD 단위 테스트 (이슈 #59).
 *
 * connect upsert(신규/갱신), disconnect 소유자 스코프(없으면 404), 연결 해제 시 외부 일정 정리를 검증한다.
 */
class CalendarServiceTest {

    private val connectionRepository: CalendarConnectionRepository = mock(CalendarConnectionRepository::class.java)
    private val externalEventRepository: ExternalEventRepository = mock(ExternalEventRepository::class.java)
    private val service = CalendarService(connectionRepository, externalEventRepository)

    private val owner = UUID.randomUUID()

    private fun anyConnection(): CalendarConnection {
        ArgumentMatchers.any(CalendarConnection::class.java)
        return CalendarConnection(userId = owner, provider = CalendarProvider.GOOGLE)
    }

    @Test
    fun `connect 는 신규 연결을 만들고 토큰과 CONNECTED 상태를 저장한다`() {
        `when`(connectionRepository.findByUserIdAndProvider(owner, CalendarProvider.GOOGLE))
            .thenReturn(Optional.empty())
        `when`(connectionRepository.save(anyConnection())).thenAnswer { it.arguments[0] }

        service.connect(
            owner,
            CalendarProvider.GOOGLE,
            ConnectCalendarRequest(accessToken = "token-placeholder"),
        )

        val captor = ArgumentCaptor.forClass(CalendarConnection::class.java)
        verify(connectionRepository).save(captor.capture())
        assertEquals(owner, captor.value.userId)
        assertEquals(CalendarProvider.GOOGLE, captor.value.provider)
        assertEquals(ConnectionStatus.CONNECTED, captor.value.status)
    }

    @Test
    fun `connect 는 기존 연결이 있으면 토큰을 갱신하고 새로 저장하지 않는다`() {
        val existing = CalendarConnection(
            userId = owner,
            provider = CalendarProvider.GOOGLE,
            accessToken = "old",
            status = ConnectionStatus.SYNC_FAILED,
        )
        `when`(connectionRepository.findByUserIdAndProvider(owner, CalendarProvider.GOOGLE))
            .thenReturn(Optional.of(existing))

        val result = service.connect(
            owner,
            CalendarProvider.GOOGLE,
            ConnectCalendarRequest(accessToken = "new-token-placeholder"),
        )

        assertEquals("new-token-placeholder", result.accessToken)
        assertEquals(ConnectionStatus.CONNECTED, result.status) // 재연결로 정상화
        verify(connectionRepository, org.mockito.Mockito.never()).save(anyConnection())
    }

    @Test
    fun `disconnect 는 외부 일정을 정리하고 연결을 삭제한다`() {
        val conn = CalendarConnection(userId = owner, provider = CalendarProvider.NOTION)
        `when`(connectionRepository.findByUserIdAndProvider(owner, CalendarProvider.NOTION))
            .thenReturn(Optional.of(conn))

        service.disconnect(owner, CalendarProvider.NOTION)

        verify(externalEventRepository).deleteAllByUserIdAndProvider(owner, CalendarProvider.NOTION)
        verify(connectionRepository).delete(conn)
    }

    @Test
    fun `없는(또는 타인) 연결 해제는 NotFoundException(404)으로 막는다`() {
        `when`(connectionRepository.findByUserIdAndProvider(owner, CalendarProvider.APPLE))
            .thenReturn(Optional.empty())

        assertThrows<NotFoundException> { service.disconnect(owner, CalendarProvider.APPLE) }
    }
}
