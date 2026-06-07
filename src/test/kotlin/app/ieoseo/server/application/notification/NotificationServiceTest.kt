package app.ieoseo.server.application.notification

import app.ieoseo.server.common.NotFoundException
import app.ieoseo.server.domain.notification.Notification
import app.ieoseo.server.domain.notification.NotificationType
import app.ieoseo.server.infrastructure.persistence.notification.NotificationRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NotificationService 단위 테스트 (#46) — repository mock.
 *
 * 생성 규칙은 [app.ieoseo.server.domain.notification.NotificationRules] 산출을 저장으로 위임하고,
 * 목록은 unreadCount 동반, 읽음 처리는 소유권 스코프(없으면 404)를 검증한다.
 */
class NotificationServiceTest {

    private val repository: NotificationRepository = mock(NotificationRepository::class.java)
    private val service = NotificationService(repository)
    private val userId = UUID.randomUUID()
    private val refId = UUID.randomUUID()

    /** Kotlin non-null 파라미터용 Mockito any() 헬퍼. */
    private fun anyNotification(): Notification {
        ArgumentMatchers.any(Notification::class.java)
        return notif()
    }

    private fun notif(type: NotificationType = NotificationType.DDAY): Notification = Notification(
        userId = userId,
        type = type,
        title = "토익 시험",
        body = "토익 시험이 3일 남았어요",
        refId = refId,
    )

    @Test
    fun `목록은 items 와 unreadCount 를 함께 채운다`() {
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(listOf(notif()), pageable, 1)
        `when`(repository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(page)
        `when`(repository.countByUserIdAndReadIsFalse(userId)).thenReturn(1L)

        val result = service.list(userId, pageable)

        assertEquals(1, result.items.size)
        assertEquals(1, result.unreadCount)
        assertEquals(1, result.total)
    }

    @Test
    fun `D-Day 임계일이면 알림을 저장한다`() {
        `when`(repository.save(anyNotification())).thenAnswer { it.arguments[0] }

        val created = service.notifyDday(userId, refId, title = "토익 시험", daysRemaining = 3)

        assertTrue(created != null)
        assertEquals(NotificationType.DDAY, created!!.type)
    }

    @Test
    fun `D-Day 임계일이 아니면 저장하지 않고 null 을 반환한다`() {
        val created = service.notifyDday(userId, refId, title = "토익", daysRemaining = 4)
        assertTrue(created == null)
    }

    @Test
    fun `읽음 처리는 소유자 알림을 read=true 로 갱신한다`() {
        val n = notif()
        `when`(repository.findByIdAndUserId(n.id, userId)).thenReturn(Optional.of(n))

        val updated = service.markRead(userId, n.id)

        assertTrue(updated.read)
    }

    @Test
    fun `타인 알림 읽음 처리는 404`() {
        val id = UUID.randomUUID()
        `when`(repository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> { service.markRead(userId, id) }
    }

    @Test
    fun `read-all 은 repository 갱신 건수를 그대로 반환한다`() {
        `when`(repository.markAllRead(userId)).thenReturn(2)
        assertEquals(2, service.markAllRead(userId))
    }
}
