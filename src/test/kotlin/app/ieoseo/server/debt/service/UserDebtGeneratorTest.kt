package app.ieoseo.server.debt.service

import app.ieoseo.server.debt.domain.TimeDebt
import app.ieoseo.server.debt.repository.TimeDebtRepository
import app.ieoseo.server.notification.service.NotificationService
import app.ieoseo.server.task.domain.Task
import app.ieoseo.server.task.domain.TaskState
import app.ieoseo.server.task.repository.TaskRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

/**
 * 사용자 단위 부채 생성 단위 테스트 (B-2).
 *
 * 어제 미완료 태스크를 예상시간만큼 부채로 만들고 DEBT_CREATED 알림을 보낸다. 이미 부채가 있는
 * 태스크는 건너뛴다. 기존 부채 taskId 는 [TimeDebtRepository.findTaskIdsByUserId] 로 한 번에 조회한다.
 */
class UserDebtGeneratorTest {

    private val taskRepository: TaskRepository = mock(TaskRepository::class.java)
    private val timeDebtRepository: TimeDebtRepository = mock(TimeDebtRepository::class.java)
    private val notificationService: NotificationService = mock(NotificationService::class.java)

    private val generator = UserDebtGenerator(taskRepository, timeDebtRepository, notificationService)

    private val originDate = LocalDate.of(2026, 6, 4)
    private val userId = UUID.randomUUID()

    private val unfinishedStates = listOf(
        TaskState.PENDING,
        TaskState.TODAY,
        TaskState.MISSED,
        TaskState.CARRIED,
        TaskState.OVERDUE,
    )

    private fun anyTimeDebt(): TimeDebt {
        any(TimeDebt::class.java)
        return TimeDebt(userId = userId, taskId = UUID.randomUUID(), minutes = 1, originDate = originDate)
    }

    @Test
    fun `미완료 태스크마다 예상시간만큼 부채를 만들고 알림을 보낸다`() {
        val taskId = UUID.randomUUID()
        val task = task(taskId, minutes = 90, title = "알고리즘")
        `when`(taskRepository.findAllByUserIdAndDateAndStateIn(userId, originDate, unfinishedStates))
            .thenReturn(listOf(task))
        `when`(timeDebtRepository.findTaskIdsByUserId(userId)).thenReturn(emptyList())

        val created = generator.generate(userId, originDate)

        assertEquals(1, created)
        val captor = ArgumentCaptor.forClass(TimeDebt::class.java)
        verify(timeDebtRepository).save(captor.capture())
        val debt = captor.value
        assertEquals(90, debt.minutes)
        assertEquals(taskId, debt.taskId)
        assertEquals(originDate, debt.originDate)
        verify(notificationService).notifyDebtCreated(userId, taskId, "알고리즘", 90)
    }

    @Test
    fun `이미 같은 태스크로 만든 부채가 있으면 재생성하지 않는다`() {
        val taskId = UUID.randomUUID()
        val task = task(taskId, minutes = 60, title = "영어")
        `when`(taskRepository.findAllByUserIdAndDateAndStateIn(userId, originDate, unfinishedStates))
            .thenReturn(listOf(task))
        `when`(timeDebtRepository.findTaskIdsByUserId(userId)).thenReturn(listOf(taskId))

        val created = generator.generate(userId, originDate)

        assertEquals(0, created)
        verify(timeDebtRepository, never()).save(anyTimeDebt())
        verifyNoInteractions(notificationService)
    }

    @Test
    fun `미완료 태스크가 없으면 기존 부채 조회도 하지 않고 0 을 반환한다`() {
        `when`(taskRepository.findAllByUserIdAndDateAndStateIn(userId, originDate, unfinishedStates))
            .thenReturn(emptyList())

        val created = generator.generate(userId, originDate)

        assertEquals(0, created)
        verify(timeDebtRepository, never()).findTaskIdsByUserId(userId)
        verify(timeDebtRepository, never()).save(anyTimeDebt())
        verifyNoInteractions(notificationService)
    }

    private fun task(taskId: UUID, minutes: Int, title: String): Task = Task(
        id = taskId,
        userId = userId,
        title = title,
        estimatedMinutes = minutes,
        date = originDate,
        state = TaskState.MISSED,
    )
}
