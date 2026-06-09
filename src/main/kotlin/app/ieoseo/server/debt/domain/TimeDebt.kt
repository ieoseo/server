package app.ieoseo.server.debt.domain

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
 * 시간부채(TimeDebt) Aggregate Root (FRD 5.3, "미룬 시간").
 *
 * 미완료 태스크에서 파생되며 이월/연체/탕감을 추적한다.
 * [taskId] 는 원본 Task 에 대한 느슨한 참조(UUID) — Aggregate 경계 분리.
 * 상태 전이/자동 이월의 권위는 server 도메인 계층([DebtTransitions], [TaskCarryOverPlanner]).
 *
 * 스키마: docs/06-백엔드/엔티티-스키마.md §4 (time_debts).
 */
@Entity
@Table(
    name = "time_debts",
    indexes = [
        Index(name = "idx_time_debts_user_id", columnList = "user_id"),
        Index(name = "idx_time_debts_status", columnList = "status"),
        Index(name = "idx_time_debts_origin_date", columnList = "origin_date"),
        Index(name = "idx_time_debts_task_id", columnList = "task_id"),
    ],
)
class TimeDebt(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    /** 소유자(users.id). 모든 조회/쓰기는 이 값으로 스코프된다(인증-도메인 §2). */
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    /** 원본 Task 참조(느슨한 UUID 참조). */
    @Column(name = "task_id", nullable = false)
    val taskId: UUID,

    /** 빚진 시간(분) = 원본 태스크 estimatedMinutes. */
    @Column(name = "minutes", nullable = false)
    var minutes: Int,

    /** 부채가 생긴 원래 날짜. */
    @Column(name = "origin_date", nullable = false)
    val originDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: DebtStatus = DebtStatus.PENDING,

    /** 이월 배정 날짜(자동/수동). 미배정이면 null. */
    @Column(name = "carried_to_date")
    var carriedToDate: LocalDate? = null,
) : BaseEntity() {

    init {
        require(minutes > 0) { "minutes 는 1 이상이어야 한다" }
    }
}
