package app.ieoseo.server.presentation.debt

import app.ieoseo.server.domain.debt.DebtLabels
import app.ieoseo.server.domain.debt.DebtStatus
import app.ieoseo.server.domain.debt.TimeDebt
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** 수동 이월 요청(부채 대상 날짜 지정, FRD 5.3). */
data class DebtCarryRequest(
    @field:NotNull val toDate: LocalDate,
)

/**
 * 시간부채 응답. 계약: docs/05-API/events-tasks-debts.md §3.
 *
 * @property title 원본 Task 제목(조인). 태스크를 찾지 못하면 빈 문자열.
 * @property fromLabel 발생 출처 라벨(예: "월요일", "지난주 금요일"). [DebtLabels] 산출.
 */
data class DebtResponse(
    val id: UUID,
    val taskId: UUID,
    val title: String,
    val fromLabel: String,
    val minutes: Int,
    val originDate: LocalDate,
    val status: DebtStatus,
    val carriedToDate: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        /**
         * 부채 엔티티 + 원본 태스크 제목으로 응답을 만든다.
         * [title] 은 호출부(service)가 소유자 스코프로 조인해 넘긴다(없으면 빈 문자열).
         * [fromLabel] 은 originDate 기준으로 산출한다(server 권위 일관성).
         */
        fun from(debt: TimeDebt, title: String, today: LocalDate = LocalDate.now()): DebtResponse = DebtResponse(
            id = debt.id,
            taskId = debt.taskId,
            title = title,
            fromLabel = DebtLabels.fromLabel(debt.originDate, today),
            minutes = debt.minutes,
            originDate = debt.originDate,
            status = debt.status,
            carriedToDate = debt.carriedToDate,
            createdAt = debt.createdAt,
            updatedAt = debt.updatedAt,
        )
    }
}

/**
 * 주간 부채 요약 응답. 계약: docs/05-API/events-tasks-debts.md §3 요약.
 *
 * @property weekStart 주 시작일(월요일)
 * @property totalMinutes 미해소(종료 상태 제외) 부채 총 분
 * @property byStatus 상태별 분 합계
 * @property overdue 연체 부채가 하나라도 있으면 true
 */
data class DebtSummaryResponse(
    val weekStart: LocalDate,
    val totalMinutes: Int,
    val byStatus: Map<DebtStatus, Int>,
    val overdue: Boolean,
)
