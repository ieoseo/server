package app.ieoseo.server.infrastructure.persistence.notification

import app.ieoseo.server.domain.notification.Notification
import app.ieoseo.server.domain.notification.NotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

/**
 * Notification 영속화 (#46). 모든 조회/쓰기는 소유자(userId)로 스코프한다(인증-도메인 §2).
 */
interface NotificationRepository : JpaRepository<Notification, UUID> {

    /** 소유자 스코프 목록(최신순). */
    fun findAllByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): Page<Notification>

    /** 소유자 스코프 안읽음 카운트(벨 점 표시용). */
    fun countByUserIdAndReadIsFalse(userId: UUID): Long

    /** 소유자 스코프 단건 조회(타인 알림은 비어 있음 → 404 매핑). */
    fun findByIdAndUserId(id: UUID, userId: UUID): Optional<Notification>

    /**
     * 스케줄러 알림 중복 방지용 존재 확인(소유자 스코프).
     * 동일 (type, refId, body) 알림이 이미 있으면 재생성하지 않는다.
     * body 에 임계 정보(D-Day 남은 일수 등)가 들어가므로 같은 임계의 재발송을 막는다.
     */
    fun existsByUserIdAndTypeAndRefIdAndBody(
        userId: UUID,
        type: NotificationType,
        refId: UUID?,
        body: String,
    ): Boolean

    /**
     * 소유자 스코프 안읽음 일괄 읽음 처리. 갱신된 건수를 반환한다.
     * 벌크 UPDATE 라 영속성 컨텍스트를 우회하므로 호출 후 같은 트랜잭션에서
     * 엔티티를 다시 읽지 않는다(service 가 카운트만 사용).
     */
    @Modifying
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    fun markAllRead(@Param("userId") userId: UUID): Int
}
