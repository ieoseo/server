package app.ieoseo.server.task.service

import app.ieoseo.server.debt.service.TimeDebtService
import app.ieoseo.server.global.exception.NotFoundException
import app.ieoseo.server.task.domain.Task
import app.ieoseo.server.task.domain.TaskState
import app.ieoseo.server.task.domain.TaskTransitions
import app.ieoseo.server.task.repository.TaskRepository
import app.ieoseo.server.task.dto.TaskCreateRequest
import app.ieoseo.server.task.dto.TaskUpdateRequest
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
 * 자동 이월 우선순위(FRD 5.3)는 [app.ieoseo.server.task.domain.TaskCarryOverPlanner] 가 산출한다.
 */
@Service
@Transactional(readOnly = true)
class TaskService(
    private val taskRepository: TaskRepository,
    private val timeDebtService: TimeDebtService,
) {
    fun findAll(userId: UUID, pageable: Pageable): Page<Task> =
        taskRepository.findAllByUserId(userId, pageable)

    fun findByDate(userId: UUID, date: LocalDate, pageable: Pageable): Page<Task> =
        taskRepository.findAllByUserIdAndDate(userId, date, pageable)

    fun findById(userId: UUID, id: UUID): Task =
        taskRepository.findByIdAndUserId(id, userId).orElseThrow { NotFoundException("Task", id) }

    @Transactional
    fun create(userId: UUID, request: TaskCreateRequest): Task {
        val task = request.toEntity(userId)
        // 오늘이거나 지난 날짜의 태스크는 즉시 활성(TODAY)으로 시작해 같은 날 완료(TODAY→DONE)가
        // 가능하게 한다. 미래 날짜는 PENDING 유지(당일 롤오버 시 TODAY 로 승격). FRD 5.2.
        if (!task.date.isAfter(LocalDate.now())) {
            task.state = TaskState.TODAY
        }
        return taskRepository.save(task)
    }

    @Transactional
    fun update(userId: UUID, id: UUID, request: TaskUpdateRequest): Task {
        val task = findById(userId, id)
        // 범위 불변식(startDate <= date)은 엔티티 init 에서만 검사되므로, 가변 갱신 시 경계에서 확인.
        request.startDate?.let {
            require(!it.isAfter(request.date)) { "startDate 는 date(마감)보다 뒤일 수 없다" }
        }
        task.title = request.title
        task.estimatedMinutes = request.estimatedMinutes
        task.date = request.date
        task.startDate = request.startDate
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
        // 아직 활성화되지 않은(PENDING) 태스크도 바로 완료할 수 있게 TODAY 를 경유한다.
        // 당일 추가분은 TODAY 로 생성되지만, 그 전에 만들어진 PENDING 도 완료 가능해야 한다.
        if (task.state == TaskState.PENDING) {
            task.state = TaskTransitions.require(task.state, TaskState.TODAY)
        }
        task.state = TaskTransitions.require(task.state, TaskState.DONE)
        task.actualMinutes = actualMinutes
        // 완료 시 이 태스크로 생성됐던 미룬 시간(부채)을 해소(RESOLVED).
        timeDebtService.resolveForTask(userId, id)
        return task
    }

    /** 완료 취소(reopen): DONE → TODAY 로 되돌리고 실제 소요 기록을 비운다(체크 토글 UX). */
    @Transactional
    fun reopen(userId: UUID, id: UUID): Task {
        val task = findById(userId, id)
        task.state = TaskTransitions.require(task.state, TaskState.TODAY)
        task.actualMinutes = null
        // 완료 취소 시 해소됐던 부채를 PENDING 으로 복구한다.
        timeDebtService.restoreForTask(userId, id)
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
