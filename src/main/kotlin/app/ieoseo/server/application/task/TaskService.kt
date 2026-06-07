package app.ieoseo.server.application.task

import app.ieoseo.server.common.NotFoundException
import app.ieoseo.server.domain.task.Task
import app.ieoseo.server.domain.task.TaskState
import app.ieoseo.server.domain.task.TaskTransitions
import app.ieoseo.server.infrastructure.persistence.task.TaskRepository
import app.ieoseo.server.presentation.task.TaskCreateRequest
import app.ieoseo.server.presentation.task.TaskUpdateRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * 태스크 도메인 서비스. 상태 머신(FRD 5.2)/이월(FRD 5.3) 규칙의 권위.
 *
 * 상태 전이는 [TaskTransitions] 가드를 거쳐 불법 전이를 거부한다(409 CONFLICT).
 * 자동 이월 우선순위(FRD 5.3)는 [app.ieoseo.server.domain.task.TaskCarryOverPlanner] 가 산출한다.
 */
@Service
@Transactional(readOnly = true)
class TaskService(
    private val taskRepository: TaskRepository,
) {
    fun findAll(userId: UUID, pageable: Pageable): Page<Task> =
        taskRepository.findAllByUserId(userId, pageable)

    fun findByDate(userId: UUID, date: LocalDate, pageable: Pageable): Page<Task> =
        taskRepository.findAllByUserIdAndDate(userId, date, pageable)

    fun findById(userId: UUID, id: UUID): Task =
        taskRepository.findByIdAndUserId(id, userId).orElseThrow { NotFoundException("Task", id) }

    @Transactional
    fun create(userId: UUID, request: TaskCreateRequest): Task =
        taskRepository.save(request.toEntity(userId))

    @Transactional
    fun update(userId: UUID, id: UUID, request: TaskUpdateRequest): Task {
        val task = findById(userId, id)
        task.title = request.title
        task.estimatedMinutes = request.estimatedMinutes
        task.date = request.date
        task.category = request.category
        task.eventId = request.eventId
        // 템플릿 반복 규칙 수정은 "이후 생성분에만" 반영된다(FRD 5.4). 이미 생성된 인스턴스는
        // 별도 행이라 영향받지 않는다. recurrence 미지정 시 기존 규칙을 유지한다.
        request.recurrence?.let { task.recurrence = it.toDomain() }
        return task
    }

    @Transactional
    fun complete(userId: UUID, id: UUID, actualMinutes: Int?): Task {
        val task = findById(userId, id)
        task.state = TaskTransitions.require(task.state, TaskState.DONE)
        task.actualMinutes = actualMinutes
        return task
    }

    @Transactional
    fun carry(userId: UUID, id: UUID, toDate: LocalDate): Task {
        val task = findById(userId, id)
        task.state = TaskTransitions.require(task.state, TaskState.CARRIED)
        task.fromDate = task.fromDate ?: task.date
        task.date = toDate
        return task
    }

    @Transactional
    fun abandon(userId: UUID, id: UUID): Task {
        val task = findById(userId, id)
        task.state = TaskTransitions.require(task.state, TaskState.ABANDONED)
        return task
    }

    @Transactional
    fun delete(userId: UUID, id: UUID) {
        val task = findById(userId, id)
        taskRepository.delete(task)
    }
}
