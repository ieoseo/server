package app.ieoseo.server.task.controller

import app.ieoseo.server.task.dto.*

import app.ieoseo.server.global.security.AuthPrincipal
import app.ieoseo.server.global.common.ApiResponse
import app.ieoseo.server.task.service.TaskService
import app.ieoseo.server.event.controller.toMeta
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

/**
 * 태스크 CRUD + 상태/이월 액션. 계약: `docs/05-API/events-tasks-debts.md`.
 * 상태 전이/이월 규칙은 TaskService(server 권위).
 */
@RestController
@RequestMapping("/api/v1/tasks")
class TaskController(
    private val taskService: TaskService,
) {
    /** `date` 쿼리(ymd, 예: 2026-06-04)가 있으면 해당 일자 태스크만. */
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?,
        pageable: Pageable,
    ): ApiResponse<List<TaskResponse>> {
        val page = if (date != null) {
            taskService.findByDate(principal.userId, date, pageable)
        } else {
            taskService.findAll(principal.userId, pageable)
        }
        return ApiResponse.ok(page.content.map(TaskResponse::from), page.toMeta())
    }

    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
    ): ApiResponse<TaskResponse> =
        ApiResponse.ok(TaskResponse.from(taskService.findById(principal.userId, id)))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: TaskCreateRequest,
    ): ApiResponse<TaskResponse> =
        ApiResponse.ok(TaskResponse.from(taskService.create(principal.userId, request)))

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
        @Valid @RequestBody request: TaskUpdateRequest,
    ): ApiResponse<TaskResponse> =
        ApiResponse.ok(TaskResponse.from(taskService.update(principal.userId, id, request)))

    @PostMapping("/{id}/complete")
    fun complete(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
        @Valid @RequestBody(required = false) request: TaskCompleteRequest?,
    ): ApiResponse<TaskResponse> =
        ApiResponse.ok(TaskResponse.from(taskService.complete(principal.userId, id, request?.actualMinutes)))

    @PostMapping("/{id}/carry")
    fun carry(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
        @Valid @RequestBody request: TaskCarryRequest,
    ): ApiResponse<TaskResponse> =
        ApiResponse.ok(TaskResponse.from(taskService.carry(principal.userId, id, request.toDate)))

    @PostMapping("/{id}/abandon")
    fun abandon(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
    ): ApiResponse<TaskResponse> =
        ApiResponse.ok(TaskResponse.from(taskService.abandon(principal.userId, id)))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
    ) = taskService.delete(principal.userId, id)
}
