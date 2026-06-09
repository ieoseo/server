package app.ieoseo.server.debt.controller

import app.ieoseo.server.debt.dto.*

import app.ieoseo.server.global.security.AuthPrincipal
import app.ieoseo.server.global.common.ApiResponse
import app.ieoseo.server.debt.domain.DebtStatus
import app.ieoseo.server.debt.service.TimeDebtService
import app.ieoseo.server.event.controller.toMeta
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 시간부채("미룬 시간") 목록/요약 + 이월/탕감 액션. 계약: docs/05-API/events-tasks-debts.md §3.
 * 상태 전이/자동 이월 권위는 TimeDebtService(server).
 */
@RestController
@RequestMapping("/api/v1/debts")
class DebtController(
    private val timeDebtService: TimeDebtService,
) {
    /** 부채 목록. `status` 쿼리로 상태 필터. */
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @RequestParam(required = false) status: DebtStatus?,
        pageable: Pageable,
    ): ApiResponse<List<DebtResponse>> {
        val page = timeDebtService.findAllResponses(principal.userId, pageable, status)
        return ApiResponse.ok(page.content, page.toMeta())
    }

    /** 주간 부채 요약. */
    @GetMapping("/summary")
    fun summary(@AuthenticationPrincipal principal: AuthPrincipal): ApiResponse<DebtSummaryResponse> =
        ApiResponse.ok(timeDebtService.summary(principal.userId))

    /** 수동 이월(대상 날짜 지정). */
    @PostMapping("/{id}/carry")
    fun carry(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
        @Valid @RequestBody request: DebtCarryRequest,
    ): ApiResponse<DebtResponse> =
        ApiResponse.ok(timeDebtService.carry(principal.userId, id, request.toDate))

    /** 자동 이월(우선순위 산출, FRD 5.3). */
    @PostMapping("/{id}/auto-carry")
    fun autoCarry(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
    ): ApiResponse<DebtResponse> =
        ApiResponse.ok(timeDebtService.autoCarry(principal.userId, id))

    /** 탕감(내려놓기). */
    @PostMapping("/{id}/abandon")
    fun abandon(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
    ): ApiResponse<DebtResponse> =
        ApiResponse.ok(timeDebtService.abandon(principal.userId, id))
}
