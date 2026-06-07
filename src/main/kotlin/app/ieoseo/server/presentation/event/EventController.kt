package app.ieoseo.server.presentation.event

import app.ieoseo.server.infrastructure.security.AuthPrincipal
import app.ieoseo.server.common.ApiResponse
import app.ieoseo.server.common.Meta
import app.ieoseo.server.application.event.EventService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 이벤트 CRUD. 계약: `docs/05-API/events-tasks-debts.md`.
 * controller 는 얇게 — 검증/매핑만, 규칙은 EventService.
 */
@RestController
@RequestMapping("/api/v1/events")
class EventController(
    private val eventService: EventService,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: AuthPrincipal,
        pageable: Pageable,
    ): ApiResponse<List<EventResponse>> {
        val page = eventService.findAll(principal.userId, pageable)
        val items = page.content.map { EventResponse.from(it, eventService.dDay(it)) }
        return ApiResponse.ok(items, page.toMeta())
    }

    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
    ): ApiResponse<EventResponse> {
        val event = eventService.findById(principal.userId, id)
        return ApiResponse.ok(EventResponse.from(event, eventService.dDay(event)))
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: EventCreateRequest,
    ): ApiResponse<EventResponse> =
        ApiResponse.ok(EventResponse.from(eventService.create(principal.userId, request)))

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
        @Valid @RequestBody request: EventUpdateRequest,
    ): ApiResponse<EventResponse> =
        ApiResponse.ok(EventResponse.from(eventService.update(principal.userId, id, request)))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
    ) = eventService.delete(principal.userId, id)
}

internal fun <T : Any> Page<T>.toMeta(): Meta = Meta(
    page = number,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages,
)
