package app.ieoseo.server.debt.service

import app.ieoseo.server.debt.domain.DebtStatus
import app.ieoseo.server.task.domain.Task
import app.ieoseo.server.task.domain.TaskState
import app.ieoseo.server.debt.domain.TimeDebt
import app.ieoseo.server.task.repository.TaskRepository
import app.ieoseo.server.debt.repository.TimeDebtRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 시간부채 서비스 단위 테스트 (FRD 5.3).
 *
 * 주간 요약 집계와 자동 이월 우선순위 위임을 mock 리포지토리로 검증한다.
 */
class TimeDebtServiceTest {

    private val timeDebtRepository: TimeDebtRepository = mock(TimeDebtRepository::class.java)
    private val taskRepository: TaskRepository = mock(TaskRepository::class.java)
    private val maxDailyMinutes = 480
    private val service = TimeDebtService(timeDebtRepository, taskRepository, maxDailyMinutes)

    private val monday = LocalDate.of(2026, 6, 1) // 월요일
    private val userId = UUID.randomUUID()

    @Test
    fun `주간 요약은 활성 부채만 합산하고 종료 상태는 제외한다`() {
        val wednesday = monday.plusDays(2)
        `when`(timeDebtRepository.findAllByUserIdAndOriginDateBetween(userId, monday, monday.plusDays(6)))
            .thenReturn(
                listOf(
                    debt(minutes = 60, status = DebtStatus.PENDING, origin = monday),
                    debt(minutes = 30, status = DebtStatus.OVERDUE, origin = wednesday),
                    debt(minutes = 999, status = DebtStatus.RESOLVED, origin = monday), // 제외
                    debt(minutes = 999, status = DebtStatus.ABANDONED, origin = monday), // 제외
                ),
            )

        val summary = service.summary(userId, today = wednesday)

        assertEquals(monday, summary.weekStart)
        assertEquals(90, summary.totalMinutes)
        assertEquals(60, summary.byStatus[DebtStatus.PENDING])
        assertEquals(30, summary.byStatus[DebtStatus.OVERDUE])
        assertTrue(summary.overdue)
    }

    @Test
    fun `부채 목록 응답은 원본 태스크 제목과 출처 라벨을 조인한다`() {
        val wednesday = monday.plusDays(2)
        val taskId = UUID.randomUUID()
        val debt = debt(minutes = 60, status = DebtStatus.PENDING, origin = monday, taskId = taskId)

        `when`(timeDebtRepository.findAllByUserId(userId, Pageable.unpaged()))
            .thenReturn(PageImpl(listOf(debt)))
        `when`(taskRepository.findByIdAndUserId(taskId, userId))
            .thenReturn(
                Optional.of(
                    task(taskId = taskId, date = monday, minutes = 60, state = TaskState.MISSED, title = "알고리즘 2문제"),
                ),
            )

        val page = service.findAllResponses(userId, Pageable.unpaged(), status = null, today = wednesday)

        val item = page.content.single()
        assertEquals("알고리즘 2문제", item.title)
        assertEquals("월요일", item.fromLabel)
        assertEquals(60, item.minutes)
    }

    @Test
    fun `조인할 태스크가 없으면 제목은 빈 문자열로 둔다`() {
        val wednesday = monday.plusDays(2)
        val taskId = UUID.randomUUID()
        val debt = debt(minutes = 60, status = DebtStatus.PENDING, origin = monday, taskId = taskId)

        `when`(timeDebtRepository.findAllByUserId(userId, Pageable.unpaged()))
            .thenReturn(PageImpl(listOf(debt)))
        `when`(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.empty())

        val page = service.findAllResponses(userId, Pageable.unpaged(), status = null, today = wednesday)

        assertEquals("", page.content.single().title)
        assertEquals("월요일", page.content.single().fromLabel)
    }

    @Test
    fun `상태 필터가 있으면 해당 상태 부채만 응답한다`() {
        val wednesday = monday.plusDays(2)
        val taskId = UUID.randomUUID()
        val debt = debt(minutes = 30, status = DebtStatus.OVERDUE, origin = monday, taskId = taskId)

        `when`(timeDebtRepository.findAllByUserIdAndStatus(userId, DebtStatus.OVERDUE, Pageable.unpaged()))
            .thenReturn(PageImpl(listOf(debt)))
        `when`(taskRepository.findByIdAndUserId(taskId, userId))
            .thenReturn(Optional.of(task(taskId = taskId, date = monday, minutes = 30, state = TaskState.MISSED, title = "영어 단어")))

        val page = service.findAllResponses(userId, Pageable.unpaged(), status = DebtStatus.OVERDUE, today = wednesday)

        assertEquals("영어 단어", page.content.single().title)
        assertEquals(DebtStatus.OVERDUE, page.content.single().status)
    }

    @Test
    fun `자동 이월은 우선순위 날짜로 CARRIED 배정한다`() {
        val tuesday = monday.plusDays(1)
        val debtId = UUID.randomUUID()
        val taskId = UUID.randomUUID()
        val debt = debt(minutes = 60, status = DebtStatus.PENDING, origin = monday, taskId = taskId)

        `when`(timeDebtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt))
        // 화요일 기준: 목(30) 최소가 되도록 잔여일 부하 구성.
        `when`(taskRepository.findAllByUserIdAndDateBetween(userId, monday, monday.plusDays(13), Pageable.unpaged()))
            .thenReturn(
                PageImpl(
                    listOf(
                        task(date = monday.plusDays(2), minutes = 120, state = TaskState.TODAY), // 수
                        task(date = monday.plusDays(3), minutes = 30, state = TaskState.TODAY), // 목 (최소)
                        task(date = monday.plusDays(4), minutes = 300, state = TaskState.TODAY), // 금
                        task(date = monday.plusDays(5), minutes = 200, state = TaskState.TODAY), // 토
                        task(date = monday.plusDays(6), minutes = 200, state = TaskState.TODAY), // 일
                    ),
                ),
            )
        `when`(taskRepository.findByIdAndUserId(taskId, userId))
            .thenReturn(Optional.of(task(taskId = taskId, date = monday, minutes = 60, state = TaskState.MISSED)))

        val result = service.autoCarry(userId, debtId, today = tuesday)

        assertEquals(DebtStatus.CARRIED, result.status)
        assertEquals(monday.plusDays(3), result.carriedToDate) // 목요일
    }

    @Test
    fun `같은 주에 둘 수 없으면 자동 이월은 다음 주 월요일 연체로 배정한다`() {
        val friday = monday.plusDays(4)
        val debtId = UUID.randomUUID()
        val taskId = UUID.randomUUID()
        val debt = debt(minutes = 60, status = DebtStatus.PENDING, origin = monday, taskId = taskId)

        `when`(timeDebtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt))
        // 금요일 기준 잔여일(토/일) 모두 8시간 초과.
        `when`(taskRepository.findAllByUserIdAndDateBetween(userId, monday, monday.plusDays(13), Pageable.unpaged()))
            .thenReturn(
                PageImpl(
                    listOf(
                        task(date = monday.plusDays(5), minutes = 480, state = TaskState.TODAY), // 토
                        task(date = monday.plusDays(6), minutes = 460, state = TaskState.TODAY), // 일
                    ),
                ),
            )
        `when`(taskRepository.findByIdAndUserId(taskId, userId)).thenReturn(Optional.empty())

        val result = service.autoCarry(userId, debtId, today = friday)

        assertEquals(DebtStatus.OVERDUE, result.status)
        assertEquals(monday.plusDays(7), result.carriedToDate) // 다음 주 월요일
    }

    private fun debt(
        minutes: Int,
        status: DebtStatus,
        origin: LocalDate,
        taskId: UUID = UUID.randomUUID(),
    ): TimeDebt = TimeDebt(
        userId = userId,
        taskId = taskId,
        minutes = minutes,
        originDate = origin,
        status = status,
    )

    private fun task(
        taskId: UUID = UUID.randomUUID(),
        date: LocalDate,
        minutes: Int,
        state: TaskState,
        title: String = "t",
    ): Task = Task(
        id = taskId,
        userId = userId,
        title = title,
        estimatedMinutes = minutes,
        date = date,
        state = state,
    )
}
