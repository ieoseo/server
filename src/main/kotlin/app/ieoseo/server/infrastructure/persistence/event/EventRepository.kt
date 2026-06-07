package app.ieoseo.server.infrastructure.persistence.event

import app.ieoseo.server.domain.event.Event
import app.ieoseo.server.domain.event.EventType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

/**
 * Event Aggregate 영속화. 도메인 규칙은 service 계층에 둔다.
 *
 * 모든 조회는 소유자(userId)로 스코프한다(인증-도메인 §2). 타인 리소스는 조회되지 않으므로
 * service 가 NotFoundException(404)으로 매핑해 존재 노출을 회피한다.
 */
interface EventRepository : JpaRepository<Event, UUID> {

    /** 소유자 스코프 단건 조회. */
    fun findByIdAndUserId(id: UUID, userId: UUID): Optional<Event>

    /** 소유자 스코프 목록. */
    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Event>

    fun findAllByUserIdAndType(userId: UUID, type: EventType, pageable: Pageable): Page<Event>

    fun findAllByUserIdAndPinnedTrue(userId: UUID, pageable: Pageable): Page<Event>

    /** T1 단일 목표일 기준 범위 조회(소유자 스코프). */
    fun findAllByUserIdAndDateBetween(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
        pageable: Pageable,
    ): Page<Event>
}
