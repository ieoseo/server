package app.ieoseo.server.infrastructure.persistence.task

import app.ieoseo.server.domain.task.Task
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
 * TaskRepository 소유권 스코프 슬라이스 테스트 (@DataJpaTest + H2, #30).
 *
 * 목록/일자 조회는 userId 로만 스코프되고, 단건 조회는 타인 태스크를 찾지 못함(404 매핑)을 검증한다.
 */
@DataJpaTest
@ImportAutoConfiguration(DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class)
@TestPropertySource(
    properties = [
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class TaskRepositoryDataJpaTest {

    @Autowired
    lateinit var repository: TaskRepository

    private val day = LocalDate.of(2026, 6, 4)
    private val owner = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Test
    fun `일자 목록은 userId 로만 스코프된다`() {
        repository.save(task())
        repository.save(task())
        repository.save(task(userId = other)) // 타인

        val mine = repository.findAllByUserIdAndDate(owner, day, PageRequest.of(0, 10))

        assertEquals(2, mine.totalElements)
    }

    @Test
    fun `단건 조회는 타인 태스크를 찾지 못한다`() {
        val mine = repository.save(task())
        val theirs = repository.save(task(userId = other))

        assertTrue(repository.findByIdAndUserId(mine.id, owner).isPresent)
        assertTrue(repository.findByIdAndUserId(theirs.id, owner).isEmpty) // 타인 → 404 매핑
    }

    private fun task(userId: UUID = owner): Task = Task(
        userId = userId,
        title = "알고리즘 2문제 풀기",
        estimatedMinutes = 60,
        date = day,
    )
}
