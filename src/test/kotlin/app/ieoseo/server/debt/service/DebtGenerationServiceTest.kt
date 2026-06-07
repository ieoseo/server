package app.ieoseo.server.debt.service

import app.ieoseo.server.notification.service.NotificationService
import app.ieoseo.server.user.domain.User
import app.ieoseo.server.debt.domain.TimeDebt
import app.ieoseo.server.task.domain.Task
import app.ieoseo.server.task.domain.TaskState
import app.ieoseo.server.user.repository.UserRepository
import app.ieoseo.server.debt.repository.TimeDebtRepository
import app.ieoseo.server.task.repository.TaskRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

/**
 * 자정 부채 생성 잡 단위 테스트 (#55, FRD 5.3).
 *
 * 어제까지 미완료(미DONE)로 남은 태스크를 감지해 예상시간만큼 TimeDebt 를 만들고
 * DEBT_CREATED 알림을 보낸다. 이미 같은 태스크로 만든 부채가 있으면 재생성하지 않는다.
 * 전 사용자 순회를 mock 으로 검증한다(크론은 테스트하지 않음).
 *
 * 매처/리터럴 혼용을 피하기 위해 stub 은 리터럴 인자만 쓴다(서비스가 넘기는 상태 목록과 동일 순서).
 */
class DebtGenerationServiceTest {

    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val taskRepository: TaskRepository = mock(TaskRepository::class.java)
    private val timeDebtRepository: TimeDebtRepository = mock(TimeDebtRepository::class.java)
    private val notificationService: NotificationService = mock(NotificationService::class.java)

    private val service = DebtGenerationService(
        userRepository,
        taskRepository,
        timeDebtRepository,
        notificationService,
    )

    private val today = LocalDate.of(2026, 6, 5) // 금요일
    private val yesterday = today.minusDays(1)
    private val userId = UUID.randomUUID()

    /** 서비스가 부채 후보로 보는 미완료 상태(DebtGenerationService.unfinishedStates 와 동일 순서). */
    private val unfinishedStates = listOf(
        TaskState.PENDING,
        TaskState.TODAY,
        TaskState.MISSED,
        TaskState.CARRIED,
        TaskState.OVERDUE,
    )

    /** Kotlin non-null 파라미터용 Mockito any() 헬퍼. */
    private fun anyTimeDebt(): TimeDebt {
        any(TimeDebt::class.java)
        return TimeDebt(userId = userId, taskId = UUID.randomUUID(), minutes = 1, originDate = yesterday)
    }

    @Test
    fun `어제 미완료 태스크마다 예상시간만큼 부채를 만들고 알림을 보낸다`() {
        val taskId = UUID.randomUUID()
        val task = task(taskId, date = yesterday, minutes = 90, state = TaskState.MISSED, title = "알고리즘")
        `when`(userRepository.findAll()).thenReturn(listOf(user(userId)))
        `when`(taskRepository.findAllByUserIdAndDateAndStateIn(userId, yesterday, unfinishedStates))
            .thenReturn(listOf(task))
        `when`(timeDebtRepository.findAllByUserIdAndTaskId(userId, taskId)).thenReturn(emptyList())

        val created = service.run(today)

        assertEquals(1, created)
        val captor = ArgumentCaptor.forClass(TimeDebt::class.java)
        verify(timeDebtRepository).save(captor.capture())
        val debt = captor.value
        assertEquals(90, debt.minutes)
        assertEquals(taskId, debt.taskId)
        assertEquals(yesterday, debt.originDate)
        verify(notificationService).notifyDebtCreated(userId, taskId, "알고리즘", 90)
    }

    @Test
    fun `이미 같은 태스크로 만든 부채가 있으면 재생성하지 않는다`() {
        val taskId = UUID.randomUUID()
        val task = task(taskId, date = yesterday, minutes = 60, state = TaskState.MISSED, title = "영어")
        `when`(userRepository.findAll()).thenReturn(listOf(user(userId)))
        `when`(taskRepository.findAllByUserIdAndDateAndStateIn(userId, yesterday, unfinishedStates))
            .thenReturn(listOf(task))
        `when`(timeDebtRepository.findAllByUserIdAndTaskId(userId, taskId))
            .thenReturn(listOf(TimeDebt(userId = userId, taskId = taskId, minutes = 60, originDate = yesterday)))

        val created = service.run(today)

        assertEquals(0, created)
        verify(timeDebtRepository, never()).save(anyTimeDebt())
        verifyNoInteractions(notificationService)
    }

    @Test
    fun `여러 사용자를 순회하며 각자 어제 미완료를 처리한다`() {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()
        val taskA = UUID.randomUUID()
        val taskB = UUID.randomUUID()
        `when`(userRepository.findAll()).thenReturn(listOf(user(userA), user(userB)))
        `when`(taskRepository.findAllByUserIdAndDateAndStateIn(userA, yesterday, unfinishedStates))
            .thenReturn(listOf(task(taskA, yesterday, 30, TaskState.MISSED, "A", userA)))
        `when`(taskRepository.findAllByUserIdAndDateAndStateIn(userB, yesterday, unfinishedStates))
            .thenReturn(listOf(task(taskB, yesterday, 45, TaskState.MISSED, "B", userB)))
        `when`(timeDebtRepository.findAllByUserIdAndTaskId(userA, taskA)).thenReturn(emptyList())
        `when`(timeDebtRepository.findAllByUserIdAndTaskId(userB, taskB)).thenReturn(emptyList())

        val created = service.run(today)

        assertEquals(2, created)
        verify(timeDebtRepository, times(2)).save(anyTimeDebt())
    }

    private fun user(id: UUID): User = User(
        id = id,
        email = "u$id@example.com",
        nickname = "n",
    )

    private fun task(
        taskId: UUID,
        date: LocalDate,
        minutes: Int,
        state: TaskState,
        title: String,
        owner: UUID = userId,
    ): Task = Task(
        id = taskId,
        userId = owner,
        title = title,
        estimatedMinutes = minutes,
        date = date,
        state = state,
    )
}
