package app.ieoseo.server.presentation.notification

import app.ieoseo.server.infrastructure.security.AuthPrincipal
import app.ieoseo.server.common.ApiResponse
import app.ieoseo.server.application.notification.NotificationService
import org.springframework.data.domain.Pageable
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 인앱 알림 조회/읽음 처리 (#46, FRD 5.6). 계약: `docs/05-API/notifications.md`.
 *
 * 인증 필수 — 요청 주체(userId)로 소유권 스코프(인증-도메인 §2). 미인증은 401,
 * 타인/없는 알림은 404. 모든 응답은 공통 [ApiResponse] envelope. OS 푸시는 범위 외(후속).
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {
    /** 목록 + unreadCount. 페이지네이션은 표준 쿼리(page/size/sort). */
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: AuthPrincipal,
        pageable: Pageable,
    ): ApiResponse<NotificationListResponse> =
        ApiResponse.ok(NotificationListResponse.from(notificationService.list(principal.userId, pageable)))

    /** 단건 읽음 처리. */
    @PatchMapping("/{id}/read")
    fun markRead(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @PathVariable id: UUID,
    ): ApiResponse<NotificationResponse> =
        ApiResponse.ok(NotificationResponse.from(notificationService.markRead(principal.userId, id)))

    /** 전체 읽음 처리(갱신 건수 반환). */
    @PostMapping("/read-all")
    fun markAllRead(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<ReadAllResponse> =
        ApiResponse.ok(ReadAllResponse(notificationService.markAllRead(principal.userId)))
}
