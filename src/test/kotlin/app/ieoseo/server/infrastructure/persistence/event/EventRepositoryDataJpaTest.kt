package app.ieoseo.server.infrastructure.persistence.event

import app.ieoseo.server.domain.event.Event
import app.ieoseo.server.domain.event.EventType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EventRepository 소유권 스코프 슬라이스 테스트 (@DataJpaTest + H2, #30).
 *
 * 목록은 userId 로만 스코프되고, 단건 조회는 타인 이벤트를 찾지 못함(404 매핑)을 검증한다.
 */
@DataJpaTest
@ImportAutoConfiguration(DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class)
@TestPropertySource(
    properties = [
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class EventRepositoryDataJpaTest {

    @Autowired
    lateinit var repository: EventRepository

    private val owner = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Test
    fun `목록은 userId 로만 스코프된다`() {
        repository.save(event())
        repository.save(event())
        repository.save(event(userId = other)) // 타인

        val mine = repository.findAllByUserId(owner, PageRequest.of(0, 10))

        assertEquals(2, mine.totalElements)
    }

    @Test
    fun `단건 조회는 타인 이벤트를 찾지 못한다`() {
        val mine = repository.save(event())
        val theirs = repository.save(event(userId = other))

        assertTrue(repository.findByIdAndUserId(mine.id, owner).isPresent)
        assertTrue(repository.findByIdAndUserId(theirs.id, owner).isEmpty) // 타인 → 404 매핑
    }

    private fun event(userId: UUID = owner): Event = Event(
        userId = userId,
        type = EventType.T1_DDAY,
        title = "정처기 실기",
        date = LocalDate.of(2026, 8, 2),
    )
}
