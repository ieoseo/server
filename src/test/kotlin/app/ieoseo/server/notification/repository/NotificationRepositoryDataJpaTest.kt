package app.ieoseo.server.notification.repository

import app.ieoseo.server.notification.domain.Notification
import app.ieoseo.server.notification.domain.NotificationType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NotificationRepository 소유권 스코프 슬라이스 테스트 (@DataJpaTest + H2, #46).
 *
 * 목록/안읽음 카운트/단건 조회는 모두 userId 로만 스코프된다(인증-도메인 §2).
 */
@DataJpaTest
@ImportAutoConfiguration(DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class)
@TestPropertySource(
    properties = [
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class NotificationRepositoryDataJpaTest {

    @Autowired
    lateinit var repository: NotificationRepository

    private val owner = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Test
    fun `목록은 userId 로만 스코프된다`() {
        repository.save(notif())
        repository.save(notif())
        repository.save(notif(userId = other)) // 타인

        val mine = repository.findAllByUserIdOrderByCreatedAtDesc(owner, PageRequest.of(0, 10))

        assertEquals(2, mine.totalElements)
    }

    @Test
    fun `안읽음 카운트는 userId 스코프에서 read=false 만 센다`() {
        repository.save(notif())
        repository.save(notif().apply { read = true })
        repository.save(notif(userId = other)) // 타인 안읽음은 제외

        assertEquals(1, repository.countByUserIdAndReadIsFalse(owner))
    }

    @Test
    fun `단건 조회는 타인 알림을 찾지 못한다`() {
        val mine = repository.save(notif())
        val theirs = repository.save(notif(userId = other))

        assertTrue(repository.findByIdAndUserId(mine.id, owner).isPresent)
        assertTrue(repository.findByIdAndUserId(theirs.id, owner).isEmpty) // 타인 → 404 매핑
    }

    @Test
    fun `read-all 은 userId 스코프 안읽음만 갱신한다`() {
        repository.save(notif())
        repository.save(notif())
        repository.save(notif(userId = other))

        val updated = repository.markAllRead(owner)

        assertEquals(2, updated)
        assertEquals(0, repository.countByUserIdAndReadIsFalse(owner))
        assertEquals(1, repository.countByUserIdAndReadIsFalse(other))
    }

    private fun notif(userId: UUID = owner): Notification = Notification(
        userId = userId,
        type = NotificationType.DDAY,
        title = "토익 시험",
        body = "토익 시험이 3일 남았어요",
        refId = UUID.randomUUID(),
    )
}
