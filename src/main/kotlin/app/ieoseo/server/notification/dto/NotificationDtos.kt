package app.ieoseo.server.notification.dto

import app.ieoseo.server.notification.domain.Notification
import app.ieoseo.server.notification.domain.NotificationType
import app.ieoseo.server.notification.service.NotificationList
import java.time.Instant
import java.util.UUID

/**
 * 알림 단건 응답. 파생 표현(아이콘/톤)은 client 가 [type] 으로 매핑한다.
 * 계약: `docs/05-API/notifications.md`.
 */
data class NotificationResponse(
    val id: UUID,
    val type: NotificationType,
    val title: String,
    val body: String,
    val refId: UUID?,
    val read: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(n: Notification): NotificationResponse = NotificationResponse(
            id = n.id,
            type = n.type,
            title = n.title,
            body = n.body,
            refId = n.refId,
            read = n.read,
            createdAt = n.createdAt,
        )
    }
}

/** 목록 응답: 항목 배열 + 안읽음 카운트(벨 점 표시용). */
data class NotificationListResponse(
    val items: List<NotificationResponse>,
    val unreadCount: Int,
) {
    companion object {
        fun from(list: NotificationList): NotificationListResponse = NotificationListResponse(
            items = list.items.map(NotificationResponse::from),
            unreadCount = list.unreadCount,
        )
    }
}

/** read-all 응답: 읽음 처리된 건수. */
data class ReadAllResponse(val updated: Int)
