package app.ieoseo.server.domain.notification

import app.ieoseo.server.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.util.UUID

/**
 * 인앱 알림(Notification) 엔티티 (FRD 5.6).
 *
 * 소유자([userId])로 스코프되는 기록이다. 생성 규칙은 [NotificationRules] 가 순수 산출하고
 * service 가 저장한다. [refId] 는 알림이 가리키는 도메인 리소스(이벤트/태스크 등)의 느슨한
 * UUID 참조(Aggregate 경계 분리, 선택). [read] 는 시트 열람/항목 탭 시 true 로 전이한다.
 *
 * 파생 표현(아이콘/톤)은 client 가 [type] 으로 매핑한다. OS 푸시는 범위 외(후속).
 */
@Entity
@Table(
    name = "notifications",
    indexes = [
        Index(name = "idx_notifications_user_id", columnList = "user_id"),
        Index(name = "idx_notifications_user_read", columnList = "user_id, read"),
    ],
)
class Notification(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    /** 소유자(users.id). 모든 조회/쓰기는 이 값으로 스코프된다(인증-도메인 §2). */
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    val type: NotificationType,

    @Column(name = "title", nullable = false, length = 120)
    val title: String,

    @Column(name = "body", nullable = false, length = 280)
    val body: String,

    /** 알림이 가리키는 도메인 리소스(이벤트/태스크 등)의 느슨한 UUID 참조(선택). */
    @Column(name = "ref_id")
    val refId: UUID? = null,

    /** 안 읽음(false)/읽음(true). 시트 열람/항목 탭 시 true 로 전이한다. */
    @Column(name = "read", nullable = false)
    var read: Boolean = false,
) : BaseEntity() {

    init {
        require(title.isNotBlank()) { "title 은 비어 있을 수 없다" }
        require(body.isNotBlank()) { "body 는 비어 있을 수 없다" }
    }
}
