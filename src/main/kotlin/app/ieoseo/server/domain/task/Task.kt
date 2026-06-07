package app.ieoseo.server.domain.task

import app.ieoseo.server.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

/**
 * 할 일(Task) Aggregate Root (FRD 4.6 / 4.7 / 5.2 / 5.3).
 *
 * 시간 빌려쓰기(자동 이월) 시:
 * - [fromDate] 에 원래 예정일을 기록하고 [date] 를 이월 대상일로 갱신
 * - [state] 를 [TaskState.CARRIED] 등으로 전이
 * 완료 시 [actualMinutes] 에 실제 소요 시간을 기록한다.
 *
 * 외부 키([eventId])는 느슨한 참조(UUID)로 둔다 — Aggregate 경계 분리.
 * 상태 전이/이월 규칙의 권위는 server 도메인 서비스(TaskCarryOverService)에 있다.
 */
@Entity
@Table(
    name = "tasks",
    indexes = [
        Index(name = "idx_tasks_user_id", columnList = "user_id"),
        Index(name = "idx_tasks_date", columnList = "date"),
        Index(name = "idx_tasks_state", columnList = "state"),
        Index(name = "idx_tasks_event_id", columnList = "event_id"),
    ],
)
class Task(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    /** 소유자(users.id). 모든 조회/쓰기는 이 값으로 스코프된다(인증-도메인 §2). */
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    /** 예상 소요 시간(분). 부채 생성 시 이 값만큼 빚진 시간으로 환산한다. */
    @Column(name = "estimated_minutes", nullable = false)
    var estimatedMinutes: Int,

    /** 현재 예정일(이월되면 갱신된다) */
    @Column(name = "date", nullable = false)
    var date: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    var state: TaskState = TaskState.PENDING,

    @Column(name = "category", length = 50)
    var category: String? = null,

    /** 연결된 이벤트(선택). Aggregate 경계 분리를 위한 느슨한 UUID 참조. */
    @Column(name = "event_id")
    var eventId: UUID? = null,

    /** 이월된 경우 원래 예정일 */
    @Column(name = "from_date")
    var fromDate: LocalDate? = null,

    /** 완료 시 실제 소요 시간(분) */
    @Column(name = "actual_minutes")
    var actualMinutes: Int? = null,

    /**
     * 반복 규칙(FRD 5.4). 이 Task 가 반복 템플릿이면 NONE 이 아니다.
     * 템플릿을 펼쳐 만든 구체 인스턴스는 [RecurrenceRule.none] 으로 둔다(재펼침 방지).
     * 템플릿 수정은 "이후 생성분에만" 반영되고 기존 인스턴스는 불변이다(정책: 도메인모델.md §3).
     */
    @Embedded
    var recurrence: RecurrenceRule = RecurrenceRule.none(),
) : BaseEntity() {

    init {
        require(title.isNotBlank()) { "title 은 비어 있을 수 없다" }
        require(estimatedMinutes > 0) { "estimatedMinutes 는 1 이상이어야 한다" }
    }
}
