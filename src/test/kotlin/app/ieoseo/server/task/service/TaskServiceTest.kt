package app.ieoseo.server.task.service

import app.ieoseo.server.global.exception.NotFoundException
import app.ieoseo.server.task.domain.Task
import app.ieoseo.server.task.domain.TaskState
import app.ieoseo.server.task.repository.TaskRepository
import app.ieoseo.server.task.dto.TaskCreateRequest
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

/**
 * TaskService 소유권 스코프 단위 테스트 (#30).
 *
 * 소유자만 조회/상태전이 가능하고, 없거나 타인 리소스는 NotFoundException(404)으로 차단됨을 검증한다.
 */
class TaskServiceTest {

    private val taskRepository: TaskRepository = mock(TaskRepository::class.java)
    private val service = TaskService(taskRepository)

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
    fun `타인 태스크 완료(상태전이)는 404 로 차단된다`() {
        val id = UUID.randomUUID()
        `when`(taskRepository.findByIdAndUserId(id, owner)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> { service.complete(owner, id, 75) }
    }
}
