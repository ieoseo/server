package app.ieoseo.server.event.service

import app.ieoseo.server.global.exception.NotFoundException
import app.ieoseo.server.event.domain.DDayCalculator
import app.ieoseo.server.event.domain.DDayResult
import app.ieoseo.server.event.domain.Event
import app.ieoseo.server.event.domain.EventValidation
import app.ieoseo.server.event.repository.EventRepository
import app.ieoseo.server.event.dto.EventCreateRequest
import app.ieoseo.server.event.dto.EventUpdateRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * 이벤트 도메인 서비스. controller 는 얇게 두고 규칙은 여기에 둔다.
 *
 * - 타입별 날짜 조합 검증은 [EventValidation] 으로 강제(FRD 5.1).
 * - D-Day/진행률/긴급도는 [DDayCalculator] 로 server 권위 계산해 응답에 싣는다.
 */
@Service
@Transactional(readOnly = true)
class EventService(
    private val eventRepository: EventRepository,
) {
    fun findAll(userId: UUID, pageable: Pageable): Page<Event> =
        eventRepository.findAllByUserId(userId, pageable)

    fun findById(userId: UUID, id: UUID): Event =
        eventRepository.findByIdAndUserId(id, userId).orElseThrow { NotFoundException("Event", id) }

    /** 이벤트의 D-Day/진행률/긴급도 파생 계산(server 권위, 저장하지 않음). */
    fun dDay(event: Event, today: LocalDate = LocalDate.now()): DDayResult =
        DDayCalculator.calculate(event, today)

    @Transactional
    fun create(userId: UUID, request: EventCreateRequest): Event {
        EventValidation.requireValidDates(request.type, request.date, request.startDate, request.endDate)
        return eventRepository.save(request.toEntity(userId))
    }

    @Transactional
    fun update(userId: UUID, id: UUID, request: EventUpdateRequest): Event {
        EventValidation.requireValidDates(request.type, request.date, request.startDate, request.endDate)
        val event = findById(userId, id)
        // 전체 치환(PUT 의미). 부분 갱신은 후속 PATCH 로 분리.
        event.type = request.type
        event.title = request.title
        event.category = request.category
        event.date = request.date
        event.startDate = request.startDate
        event.endDate = request.endDate
        event.pinned = request.pinned
        event.memo = request.memo
        event.color = request.color
        return event
    }

    @Transactional
    fun delete(userId: UUID, id: UUID) {
        val event = findById(userId, id)
        eventRepository.delete(event)
    }
}
