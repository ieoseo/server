package app.ieoseo.server.application.debt

import app.ieoseo.server.common.NotFoundException
import app.ieoseo.server.domain.debt.DebtStatus
import app.ieoseo.server.domain.debt.DebtTransitions
import app.ieoseo.server.domain.task.TaskCarryOverPlanner
import app.ieoseo.server.domain.task.TaskState
import app.ieoseo.server.domain.debt.TimeDebt
import app.ieoseo.server.infrastructure.persistence.task.TaskRepository
import app.ieoseo.server.infrastructure.persistence.debt.TimeDebtRepository
import app.ieoseo.server.presentation.debt.DebtResponse
import app.ieoseo.server.presentation.debt.DebtSummaryResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * 시간부채 도메인 서비스 (FRD 5.3). "미룬 시간"의 생성·이월·연체·탕감 권위.
 *
 * 자동 이월 우선순위는 [TaskCarryOverPlanner] (순수 함수)에 위임하고,
 * 상태 전이는 [DebtTransitions] 가드를 거친다.
 */
@Service
@Transactional(readOnly = true)
class TimeDebtService(
    private val timeDebtRepository: TimeDebtRepository,
    private val taskRepository: TaskRepository,
    @param:Value("\${ieoseo.max-daily-minutes:480}")
    private val maxDailyMinutes: Int,
) {
    fun findAll(userId: UUID, pageable: Pageable): Page<TimeDebt> =
        timeDebtRepository.findAllByUserId(userId, pageable)

    fun findByStatus(userId: UUID, status: DebtStatus, pageable: Pageable): Page<TimeDebt> =
        timeDebtRepository.findAllByUserIdAndStatus(userId, status, pageable)

    fun findById(userId: UUID, id: UUID): TimeDebt =
        timeDebtRepository.findByIdAndUserId(id, userId).orElseThrow { NotFoundException("TimeDebt", id) }

    /**
     * 부채 목록 응답(원본 태스크 제목·출처 라벨 조인, #41).
     *
     * 각 부채의 [TimeDebt.taskId] 로 소유자 스코프 태스크를 조회해 제목을 채우고,
     * originDate 기준 출처 라벨([DebtLabels])을 [today] 기준으로 산출한다. 태스크가 없으면 제목은 빈 문자열.
     */
    fun findAllResponses(
        userId: UUID,
        pageable: Pageable,
        status: DebtStatus?,
        today: LocalDate = LocalDate.now(),
    ): Page<DebtResponse> {
        val page = if (status != null) {
            timeDebtRepository.findAllByUserIdAndStatus(userId, status, pageable)
        } else {
            timeDebtRepository.findAllByUserId(userId, pageable)
        }
        // taskId → title 조인(소유자 스코프). 중복 taskId 는 한 번만 조회.
        val titles = page.content.map { it.taskId }.distinct().associateWith { taskId ->
            taskRepository.findByIdAndUserId(taskId, userId).map { it.title }.orElse("")
        }
        return page.map { DebtResponse.from(it, title = titles[it.taskId] ?: "", today = today) }
    }

    /** 주간 부채 요약(주 시작 월요일 기준). 종료 상태(RESOLVED/ABANDONED)는 총합에서 제외. */
    fun summary(userId: UUID, today: LocalDate = LocalDate.now()): DebtSummaryResponse {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        val debts = timeDebtRepository.findAllByUserIdAndOriginDateBetween(userId, weekStart, weekEnd)

        val active = debts.filter { it.status != DebtStatus.RESOLVED && it.status != DebtStatus.ABANDONED }
        val byStatus = active.groupBy { it.status }.mapValues { (_, list) -> list.sumOf { it.minutes } }

        return DebtSummaryResponse(
            weekStart = weekStart,
            totalMinutes = active.sumOf { it.minutes },
            byStatus = byStatus,
            overdue = active.any { it.status == DebtStatus.OVERDUE },
        )
    }

    /**
     * 수동 이월: 사용자가 지정한 날짜로 배정한다(CARRIED). 원본 태스크 date 도 갱신.
     */
    @Transactional
    fun carry(userId: UUID, id: UUID, toDate: LocalDate, today: LocalDate = LocalDate.now()): DebtResponse {
        val debt = findById(userId, id)
        debt.status = DebtTransitions.require(debt.status, DebtStatus.CARRIED)
        debt.carriedToDate = toDate
        applyToTask(userId, debt.taskId, toDate, TaskState.CARRIED)
        return toResponse(userId, debt, today)
    }

    /**
     * 자동 이월: [TaskCarryOverPlanner] 우선순위로 날짜를 배정한다(server 권위, FRD 5.3).
     * 같은 주에 둘 수 없으면 다음 주 월요일 + 연체(OVERDUE).
     */
    @Transactional
    fun autoCarry(userId: UUID, id: UUID, today: LocalDate = LocalDate.now()): DebtResponse {
        val debt = findById(userId, id)
        val plan = TaskCarryOverPlanner.plan(
            taskMinutes = debt.minutes,
            today = today,
            scheduledMinutesByDate = scheduledMinutesForWeek(userId, today),
            maxDailyMinutes = maxDailyMinutes,
        )
        val target = if (plan.overdue) DebtStatus.OVERDUE else DebtStatus.CARRIED
        debt.status = DebtTransitions.require(debt.status, target)
        debt.carriedToDate = plan.targetDate
        applyToTask(userId, debt.taskId, plan.targetDate, TaskState.CARRIED)
        return toResponse(userId, debt, today)
    }

    /** 탕감(내려놓기). 기록은 남기고 종료 상태로 전이. */
    @Transactional
    fun abandon(userId: UUID, id: UUID, today: LocalDate = LocalDate.now()): DebtResponse {
        val debt = findById(userId, id)
        debt.status = DebtTransitions.require(debt.status, DebtStatus.ABANDONED)
        return toResponse(userId, debt, today)
    }

    /** 단건 부채 응답(원본 태스크 제목·출처 라벨 조인, 소유자 스코프). */
    private fun toResponse(userId: UUID, debt: TimeDebt, today: LocalDate): DebtResponse {
        val title = taskRepository.findByIdAndUserId(debt.taskId, userId).map { it.title }.orElse("")
        return DebtResponse.from(debt, title = title, today = today)
    }

    /** 자동 이월 시 하루별 예약 시간 맵(완료/탕감 제외 활성 태스크의 estimatedMinutes 합, 소유자 스코프). */
    private fun scheduledMinutesForWeek(userId: UUID, today: LocalDate): Map<LocalDate, Int> {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(13) // 이번 주 + 다음 주 월요일 후보까지 포함
        return taskRepository.findAllByUserIdAndDateBetween(userId, weekStart, weekEnd, Pageable.unpaged())
            .content
            .filter { it.state != TaskState.DONE && it.state != TaskState.ABANDONED }
            .groupBy { it.date }
            .mapValues { (_, tasks) -> tasks.sumOf { it.estimatedMinutes } }
    }

    private fun applyToTask(userId: UUID, taskId: UUID, toDate: LocalDate, state: TaskState) {
        taskRepository.findByIdAndUserId(taskId, userId).ifPresent { task ->
            task.fromDate = task.fromDate ?: task.date
            task.date = toDate
            task.state = state
        }
    }
}
