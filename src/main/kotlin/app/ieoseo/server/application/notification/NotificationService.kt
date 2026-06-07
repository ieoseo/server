package app.ieoseo.server.application.notification

import app.ieoseo.server.common.NotFoundException
import app.ieoseo.server.domain.notification.Notification
import app.ieoseo.server.domain.notification.NotificationRules
import app.ieoseo.server.infrastructure.persistence.notification.NotificationRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 목록 응답 묶음: 페이지 아이템 + 안읽음 카운트(unreadCount) + 전체 건수(total).
 *
 * unreadCount 는 벨 점 표시용으로 페이지와 무관한 소유자 전체 안읽음 수다.
 */
data class NotificationList(
    val items: List<Notification>,
    val unreadCount: Int,
    val total: Long,
)

/**
 * 인앱 알림 도메인 서비스 (#46, FRD 5.6).
 *
 * 생성 규칙의 산출은 [NotificationRules] (순수)에 위임하고, 임계를 통과한 경우에만 저장한다.
 * 조회/읽음 처리는 소유자(userId) 스코프로만 동작하며 타인 리소스는 [NotFoundException](404).
 * OS 푸시(FCM/APNs)는 범위 외(후속) — 본 서비스는 인앱 알림 영속화/조회만 담당한다.
 */
@Service
@Transactional(readOnly = true)
class NotificationService(
    private val repository: NotificationRepository,
) {
    /** 소유자 스코프 목록 + 안읽음 카운트. */
    fun list(userId: UUID, pageable: Pageable): NotificationList {
        val page: Page<Notification> = repository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
        val unread = repository.countByUserIdAndReadIsFalse(userId)
        return NotificationList(
            items = page.content,
            unreadCount = unread.toInt(),
            total = page.totalElements,
        )
    }

    /** 단건 읽음 처리(소유자 스코프). 없거나 타인 → 404. */
    @Transactional
    fun markRead(userId: UUID, id: UUID): Notification {
        val notification = repository.findByIdAndUserId(id, userId)
            .orElseThrow { NotFoundException("Notification", id) }
        notification.read = true
        return notification
    }

    /** 소유자 스코프 안읽음 전체 읽음 처리. 갱신 건수를 반환한다. */
    @Transactional
    fun markAllRead(userId: UUID): Int = repository.markAllRead(userId)

    // ── 생성 규칙(입력 → 산출 → 저장). 임계 미달이면 저장하지 않고 null. ──

    @Transactional
    fun notifyDday(userId: UUID, refId: UUID?, title: String, daysRemaining: Int): Notification? =
        NotificationRules.dday(userId, refId, title, daysRemaining)?.let(repository::save)

    @Transactional
    fun notifyDebtCreated(userId: UUID, refId: UUID?, title: String, minutes: Int): Notification =
        repository.save(NotificationRules.debtCreated(userId, refId, title, minutes))

    @Transactional
    fun notifyDebtWarning(userId: UUID, totalMinutes: Int): Notification? =
        NotificationRules.debtWarning(userId, totalMinutes)?.let(repository::save)

    @Transactional
    fun notifyStreak(userId: UUID, streakDays: Int): Notification? =
        NotificationRules.streak(userId, streakDays)?.let(repository::save)

    // ── 스케줄러용 중복 방지 변형(이미 동일 알림이 있으면 저장하지 않음). ──

    /**
     * D-Day 알림(스케줄러). 임계 통과 후, 동일 (type, refId, body) 알림이 이미 있으면 재생성하지 않는다.
     * 같은 임계(예: D-3)에 대해 잡이 여러 번 돌아도 한 번만 저장된다.
     */
    @Transactional
    fun notifyDdayIfAbsent(userId: UUID, refId: UUID?, title: String, daysRemaining: Int): Notification? =
        NotificationRules.dday(userId, refId, title, daysRemaining)?.let(::saveIfAbsent)

    /**
     * 스트릭 알림(스케줄러). 축하 주기 도달 후, 동일 알림이 이미 있으면 재생성하지 않는다.
     */
    @Transactional
    fun notifyStreakIfAbsent(userId: UUID, streakDays: Int): Notification? =
        NotificationRules.streak(userId, streakDays)?.let(::saveIfAbsent)

    /** (type, refId, body) 중복이면 null, 아니면 저장. */
    private fun saveIfAbsent(candidate: Notification): Notification? {
        val exists = repository.existsByUserIdAndTypeAndRefIdAndBody(
            candidate.userId,
            candidate.type,
            candidate.refId,
            candidate.body,
        )
        return if (exists) null else repository.save(candidate)
    }
}
