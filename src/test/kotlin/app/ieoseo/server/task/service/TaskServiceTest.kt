package app.ieoseo.server.task.service

import app.ieoseo.server.debt.service.TimeDebtService
import app.ieoseo.server.global.exception.NotFoundException
import app.ieoseo.server.task.domain.Task
import app.ieoseo.server.task.domain.TaskState
import app.ieoseo.server.task.repository.TaskRepository
import app.ieoseo.server.task.dto.TaskCreateRequest
import app.ieoseo.server.task.dto.TaskUpdateRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * TaskService 소유권 스코프 단위 테스트 (#30).
 *
 * 소유자만 조회/상태전이 가능하고, 없거나 타인 리소스는 NotFoundException(404)으로 차단됨을 검증한다.
 */
class TaskServiceTest {

    private val taskRepository: TaskRepository = mock(TaskRepository::class.java)
    private val timeDebtService: TimeDebtService = mock(TimeDebtService::class.java)
    private val service = TaskService(taskRepository, timeDebtService)

    private val owner = UUID.randomUUID()

    /** Kotlin non-null 파라미터용 Mockito any() 헬퍼. */
    private fun anyTask(): Task {
        ArgumentMatchers.any(Task::class.java)
        return Task(userId = owner, title = "x", estimatedMinutes = 60, date = LocalDate.now())
    }

    @Test
    fun `생성 시 인증 주체 userId 를 엔티티에 세팅한다`() {
        val request = TaskCreateRequest(title = "알고리즘", estimatedMinutes = 60, date = LocalDate.of(2026, 6, 4))
        `when`(taskRepository.save(anyTask())).thenAnswer { it.arguments[0] }

        service.create(owner, request)

        val captor = ArgumentCaptor.forClass(Task::class.java)
        verify(taskRepository).save(captor.capture())
        assertEquals(owner, captor.value.userId)
    }

    @Test
    fun `생성 시 startDate(범위 시작)를 엔티티에 반영한다`() {
        val request = TaskCreateRequest(
            title = "여행 준비",
            estimatedMinutes = 120,
            date = LocalDate.of(2026, 6, 7),
            startDate = LocalDate.of(2026, 6, 4),
        )
        `when`(taskRepository.save(anyTask())).thenAnswer { it.arguments[0] }

        service.create(owner, request)

        val captor = ArgumentCaptor.forClass(Task::class.java)
        verify(taskRepository).save(captor.capture())
        assertEquals(LocalDate.of(2026, 6, 4), captor.value.startDate)
    }

    @Test
    fun `update 에서 startDate 가 date(마감)보다 뒤면 거부한다(400)`() {
        val id = UUID.randomUUID()
        val task = Task(id = id, userId = owner, title = "x", estimatedMinutes = 30, date = LocalDate.of(2026, 6, 4))
        `when`(taskRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.of(task))
        val request = TaskUpdateRequest(
            title = "x",
            estimatedMinutes = 30,
            date = LocalDate.of(2026, 6, 4),
            startDate = LocalDate.of(2026, 6, 7), // 마감보다 뒤 → 거부
        )

        assertThrows<IllegalArgumentException> { service.update(owner, id, request) }
    }

    @Test
    fun `오늘 날짜로 생성하면 즉시 활성(TODAY) 상태가 된다`() {
        val request = TaskCreateRequest(title = "오늘 할 일", estimatedMinutes = 30, date = LocalDate.now())
        `when`(taskRepository.save(anyTask())).thenAnswer { it.arguments[0] }

        service.create(owner, request)

        val captor = ArgumentCaptor.forClass(Task::class.java)
        verify(taskRepository).save(captor.capture())
        assertEquals(TaskState.TODAY, captor.value.state)
    }

    @Test
    fun `미래 날짜로 생성하면 PENDING 상태를 유지한다`() {
        val request = TaskCreateRequest(title = "다음 주", estimatedMinutes = 30, date = LocalDate.now().plusDays(7))
        `when`(taskRepository.save(anyTask())).thenAnswer { it.arguments[0] }

        service.create(owner, request)

        val captor = ArgumentCaptor.forClass(Task::class.java)
        verify(taskRepository).save(captor.capture())
        assertEquals(TaskState.PENDING, captor.value.state)
    }

    @Test
    fun `소유자 스코프로 단건을 조회한다`() {
        val id = UUID.randomUUID()
        val task = Task(id = id, userId = owner, title = "알고리즘", estimatedMinutes = 60, date = LocalDate.of(2026, 6, 4))
        `when`(taskRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.of(task))

        assertEquals(task, service.findById(owner, id))
    }

    @Test
    fun `없거나 타인 태스크 조회는 NotFoundException(404)으로 매핑한다`() {
        val id = UUID.randomUUID()
        `when`(taskRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> { service.findById(owner, id) }
    }

    @Test
    fun `PENDING 태스크도 완료하면 TODAY 를 경유해 DONE 으로 전이한다`() {
        val id = UUID.randomUUID()
        // 생성-시점 보정 전에 만들어진 PENDING 태스크(예: 어제까지 미래였던 항목).
        val task = Task(id = id, userId = owner, title = "x", estimatedMinutes = 30, date = LocalDate.now())
        task.state = TaskState.PENDING
        `when`(taskRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.of(task))

        val result = service.complete(owner, id, 25)

        assertEquals(TaskState.DONE, result.state)
        assertEquals(25, result.actualMinutes)
    }

    @Test
    fun `완료 취소(reopen)는 DONE 을 TODAY 로 되돌리고 actualMinutes 를 비운다`() {
        val id = UUID.randomUUID()
        val task = Task(id = id, userId = owner, title = "x", estimatedMinutes = 30, date = LocalDate.now())
        task.state = TaskState.DONE
        task.actualMinutes = 25
        `when`(taskRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.of(task))

        val result = service.reopen(owner, id)

        assertEquals(TaskState.TODAY, result.state)
        assertNull(result.actualMinutes)
        verify(timeDebtService).restoreForTask(owner, id)
    }

    @Test
    fun `과거 날짜 태스크를 reopen 하면 오늘로 끌어와 TODAY 정합을 맞춘다(F13)`() {
        val id = UUID.randomUUID()
        val today = LocalDate.of(2026, 6, 10)
        val past = today.minusDays(3)
        val task = Task(id = id, userId = owner, title = "x", estimatedMinutes = 30, date = past)
        task.state = TaskState.DONE
        `when`(taskRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.of(task))

        val result = service.reopen(owner, id, today = today)

        // state=TODAY 인데 date 가 과거면 오늘 목록에 안 잡히는 고아가 된다 → 오늘로 이동.
        assertEquals(TaskState.TODAY, result.state)
        assertEquals(today, result.date)
    }

    @Test
    fun `완료 시 연결된 부채 해소(resolveForTask)를 호출한다`() {
        val id = UUID.randomUUID()
        val task = Task(id = id, userId = owner, title = "x", estimatedMinutes = 30, date = LocalDate.now())
        task.state = TaskState.TODAY
        `when`(taskRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.of(task))

        service.complete(owner, id, 25)

        verify(timeDebtService).resolveForTask(owner, id)
    }

    @Test
    fun `MISSED 태스크도 직접 완료할 수 있다`() {
        val id = UUID.randomUUID()
        val task = Task(id = id, userId = owner, title = "x", estimatedMinutes = 30, date = LocalDate.now())
        task.state = TaskState.MISSED
        `when`(taskRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.of(task))

        val result = service.complete(owner, id, null)

        assertEquals(TaskState.DONE, result.state)
    }

    @Test
    fun `타인 태스크 완료(상태전이)는 404 로 차단된다`() {
        val id = UUID.randomUUID()
        `when`(taskRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> { service.complete(owner, id, 75) }
    }
}
