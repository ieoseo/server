package app.ieoseo.server.application.event

import app.ieoseo.server.common.NotFoundException
import app.ieoseo.server.domain.event.Event
import app.ieoseo.server.domain.event.EventType
import app.ieoseo.server.infrastructure.persistence.event.EventRepository
import app.ieoseo.server.presentation.event.EventCreateRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

/**
 * EventService 소유권 스코프 단위 테스트 (#30).
 *
 * 소유자만 조회되고, 없거나 타인 리소스는 NotFoundException(404)으로 매핑되어 존재가 노출되지 않음을 검증한다.
 * 생성 시 인증 주체 userId 가 엔티티에 세팅되는지도 확인한다.
 */
class EventServiceTest {

    private val eventRepository: EventRepository = mock(EventRepository::class.java)
    private val service = EventService(eventRepository)

    private val owner = UUID.randomUUID()

    /** Kotlin non-null 파라미터용 Mockito any() 헬퍼. */
    private fun anyEvent(): Event {
        ArgumentMatchers.any(Event::class.java)
        return Event(userId = owner, type = EventType.T1_DDAY, title = "x", date = LocalDate.now())
    }

    @Test
    fun `생성 시 인증 주체 userId 를 엔티티에 세팅한다`() {
        val request = EventCreateRequest(type = EventType.T1_DDAY, title = "정처기 실기", date = LocalDate.of(2026, 8, 2))
        `when`(eventRepository.save(anyEvent())).thenAnswer { it.arguments[0] }

        service.create(owner, request)

        val captor = ArgumentCaptor.forClass(Event::class.java)
        verify(eventRepository).save(captor.capture())
        assertEquals(owner, captor.value.userId)
    }

    @Test
    fun `소유자 스코프로 단건을 조회한다`() {
        val id = UUID.randomUUID()
        val event = Event(id = id, userId = owner, type = EventType.T1_DDAY, title = "x", date = LocalDate.of(2026, 8, 2))
        `when`(eventRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.of(event))

        assertEquals(event, service.findById(owner, id))
    }

    @Test
    fun `없거나 타인 이벤트 조회는 NotFoundException(404)으로 매핑한다`() {
        val id = UUID.randomUUID()
        `when`(eventRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> { service.findById(owner, id) }
    }

    @Test
    fun `삭제는 소유자 스코프 조회 실패 시 404 로 차단된다`() {
        val id = UUID.randomUUID()
        `when`(eventRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> { service.delete(owner, id) }
    }
}
