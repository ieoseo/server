package app.ieoseo.server.calendar.controller

import app.ieoseo.server.calendar.dto.*

import app.ieoseo.server.calendar.service.CalendarService
import app.ieoseo.server.calendar.service.CalendarSyncService
import app.ieoseo.server.global.common.ApiResponse
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.global.security.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 외부 캘린더 연동·동기화 (이슈 #59). 계약: `docs/05-API/calendar.md`.
 *
 * 모든 엔드포인트는 인증 필수(SecurityConfig 의 calendar 경로) — 요청 주체(userId)로 스코프.
 * controller 는 얇게 — 매핑/검증만, 규칙은 [CalendarService]/[CalendarSyncService].
 *
 * `{provider}` 경로는 대소문자 무시(`google`/`GOOGLE`). 미지원 값은 400 으로 매핑된다.
 */
@RestController
@RequestMapping("/api/v1/calendar")
class CalendarController(
    private val calendarService: CalendarService,
    private val calendarSyncService: CalendarSyncService,
) {
    @GetMapping("/connections")
    fun connections(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<List<CalendarConnectionResponse>> {
        val items = calendarService.listConnections(principal.userId).map(CalendarConnectionResponse::from)
        return ApiResponse.ok(items)
    }

    @PostMapping("/connections/{provider}")
    fun connect(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable provider: String,
        @Valid @RequestBody request: ConnectCalendarRequest,
    ): ApiResponse<CalendarConnectionResponse> {
        val connection = calendarService.connect(principal.userId, provider.toProvider(), request)
        return ApiResponse.ok(CalendarConnectionResponse.from(connection))
    }

    @DeleteMapping("/connections/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun disconnect(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable provider: String,
    ) = calendarService.disconnect(principal.userId, provider.toProvider())

    @PostMapping("/sync")
    fun sync(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<List<SyncResultResponse>> {
        val results = calendarSyncService.syncAll(principal.userId).map {
            SyncResultResponse(
                provider = it.connection.provider,
                imported = it.imported,
                status = it.connection.status,
                syncedAt = it.connection.lastSyncedAt,
            )
        }
        return ApiResponse.ok(results)
    }

    @GetMapping("/external")
    fun external(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ApiResponse<List<ExternalEventResponse>> {
        val items = calendarService.externalEvents(principal.userId, from, to).map(ExternalEventResponse::from)
        return ApiResponse.ok(items)
    }

    /** 경로 변수 → [CalendarProvider](대소문자 무시). 미지원 값은 IllegalArgumentException → 400. */
    private fun String.toProvider(): CalendarProvider =
        runCatching { CalendarProvider.valueOf(uppercase()) }
            .getOrElse { throw IllegalArgumentException("지원하지 않는 캘린더 provider 입니다: $this") }
}
