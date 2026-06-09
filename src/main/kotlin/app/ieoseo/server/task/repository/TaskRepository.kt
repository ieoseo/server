package app.ieoseo.server.task.repository

import app.ieoseo.server.task.domain.Task
import app.ieoseo.server.task.domain.RecurrenceFrequency
import app.ieoseo.server.task.domain.TaskState
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

/**
 * Task Aggregate 영속화. 상태 전이/이월 규칙은 service 계층에 둔다.
 *
 * 모든 조회는 소유자(userId)로 스코프한다(인증-도메인 §2).
 */
interface TaskRepository : JpaRepository<Task, UUID> {

    /** 소유자 스코프 단건 조회. */
    fun findByIdAndUserId(id: UUID, userId: UUID): Optional<Task>

    /** 소유자 스코프 목록. */
    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Task>

    fun findAllByUserIdAndDate(userId: UUID, date: LocalDate, pageable: Pageable): Page<Task>

    fun findAllByUserIdAndDateBetween(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
        pageable: Pageable,
    ): Page<Task>

    fun findAllByUserIdAndState(userId: UUID, state: TaskState, pageable: Pageable): Page<Task>

    fun findAllByUserIdAndEventId(userId: UUID, eventId: UUID, pageable: Pageable): Page<Task>

    /** 자동 이월 후보(특정일에 미완료로 남은 태스크) 조회용(소유자 스코프). */
    fun findAllByUserIdAndDateAndStateIn(
        userId: UUID,
        date: LocalDate,
        states: Collection<TaskState>,
    ): List<Task>

    /**
     * 반복 규칙이 있는 템플릿 태스크 조회(반복 인스턴스 생성 잡, 소유자 스코프).
     * 임베디드 [app.ieoseo.server.task.domain.RecurrenceRule.frequency] 가 NONE 이 아닌 행만 반환한다.
     */
    fun findAllByUserIdAndRecurrenceFrequencyNot(
        userId: UUID,
        frequency: RecurrenceFrequency,
    ): List<Task>
}
