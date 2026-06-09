package app.ieoseo.server.event.domain

import app.ieoseo.server.global.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

/**
 * D-Day 이벤트 Aggregate Root (FRD 4.8 / 5.1).
 *
 * 타입별 날짜 사용:
 * - [EventType.T1_DDAY]        : [date] (단일 목표일)
 * - [EventType.T2_PROGRESS]    : [startDate]~[endDate]
 * - [EventType.T3_PERIOD_DDAY] : [startDate]~[endDate]
 *
 * 카운트다운/진행률 계산은 표현 계층이 아닌 server 권위로 산출한다(FRD 5.1).
 * id 전략: 애플리케이션 생성 UUID(분산 친화, 클라이언트 낙관적 생성 가능).
 */
@Entity
@Table(
    name = "events",
    indexes = [
        Index(name = "idx_events_user_id", columnList = "user_id"),
        Index(name = "idx_events_date", columnList = "date"),
        Index(name = "idx_events_pinned", columnList = "pinned"),
    ],
)
class Event(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    /** 소유자(users.id). 모든 조회/쓰기는 이 값으로 스코프된다(인증-도메인 §2). */
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    var type: EventType,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "category", length = 50)
    var category: String? = null,

    /** T1 단일 목표일 */
    @Column(name = "date")
    var date: LocalDate? = null,

    /** T2/T3 기간 시작 */
    @Column(name = "start_date")
    var startDate: LocalDate? = null,

    /** T2/T3 기간 종료 */
    @Column(name = "end_date")
    var endDate: LocalDate? = null,

    @Column(name = "pinned", nullable = false)
    var pinned: Boolean = false,

    @Column(name = "memo", length = 1000)
    var memo: String? = null,

    /** 표시 색상(헥스 등). 표현용 메타데이터. */
    @Column(name = "color", length = 16)
    var color: String? = null,
) : BaseEntity() {

    init {
        // 타입별 날짜 필수 불변식은 EventValidation(EventService 경유)에서 강제한다.
        require(title.isNotBlank()) { "title 은 비어 있을 수 없다" }
    }
}
