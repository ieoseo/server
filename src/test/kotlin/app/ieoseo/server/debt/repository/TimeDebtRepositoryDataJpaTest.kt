package app.ieoseo.server.debt.repository

import app.ieoseo.server.debt.domain.DebtStatus
import app.ieoseo.server.debt.domain.TimeDebt
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
 * TimeDebtRepository 슬라이스 테스트 (@DataJpaTest + 임베디드 H2).
 *
 * 기본 test 프로파일은 DataSource/JPA auto-config 를 제외하므로(실 DB 미연결),
 * 본 슬라이스는 해당 제외를 비우고 H2 임베디드 DB 로 소유자(userId) 스코프 쿼리 메서드를 검증한다(#30).
 */
@DataJpaTest
@ImportAutoConfiguration(DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class)
@TestPropertySource(
    properties = [
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class TimeDebtRepositoryDataJpaTest {

    @Autowired
    lateinit var repository: TimeDebtRepository

    private val monday = LocalDate.of(2026, 6, 1)
    private val owner = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Test
    fun `소유자와 상태로 부채를 필터링한다`() {
        repository.save(debt(status = DebtStatus.PENDING, origin = monday))
        repository.save(debt(status = DebtStatus.OVERDUE, origin = monday))
        repository.save(debt(status = DebtStatus.PENDING, origin = monday.plusDays(1)))
        repository.save(debt(status = DebtStatus.PENDING, origin = monday, userId = other)) // 타인

        val pending = repository.findAllByUserIdAndStatus(owner, DebtStatus.PENDING, PageRequest.of(0, 10))

        assertEquals(2, pending.totalElements)
    }

    @Test
    fun `소유자 스코프로 원래 날짜 범위 부채를 조회한다`() {
        repository.save(debt(status = DebtStatus.PENDING, origin = monday))
        repository.save(debt(status = DebtStatus.PENDING, origin = monday.plusDays(6)))
        repository.save(debt(status = DebtStatus.PENDING, origin = monday.plusDays(10))) // 범위 밖
        repository.save(debt(status = DebtStatus.PENDING, origin = monday, userId = other)) // 타인

        val inWeek = repository.findAllByUserIdAndOriginDateBetween(owner, monday, monday.plusDays(6))

        assertEquals(2, inWeek.size)
    }

    @Test
    fun `소유자 스코프 단건 조회는 타인 부채를 찾지 못한다`() {
        val mine = repository.save(debt(status = DebtStatus.PENDING, origin = monday))
        val theirs = repository.save(debt(status = DebtStatus.PENDING, origin = monday, userId = other))

        assertTrue(repository.findByIdAndUserId(mine.id, owner).isPresent)
        assertTrue(repository.findByIdAndUserId(theirs.id, owner).isEmpty) // 타인 → 비어있음(404 매핑)
    }

    @Test
    fun `소유자와 taskId 로 부채를 조회한다`() {
        val taskId = UUID.randomUUID()
        repository.save(debt(status = DebtStatus.PENDING, origin = monday, taskId = taskId))
        repository.save(debt(status = DebtStatus.PENDING, origin = monday))
        repository.save(debt(status = DebtStatus.PENDING, origin = monday, taskId = taskId, userId = other)) // 타인

        assertEquals(1, repository.findAllByUserIdAndTaskId(owner, taskId).size)
    }

    @Test
    fun `소유자의 모든 부채 taskId 를 일괄 조회한다(N+1 제거용)`() {
        val taskA = UUID.randomUUID()
        val taskB = UUID.randomUUID()
        repository.save(debt(status = DebtStatus.PENDING, origin = monday, taskId = taskA))
        repository.save(debt(status = DebtStatus.PENDING, origin = monday, taskId = taskB))
        repository.save(debt(status = DebtStatus.PENDING, origin = monday, userId = other)) // 타인 제외

        val taskIds = repository.findTaskIdsByUserId(owner)

        assertEquals(setOf(taskA, taskB), taskIds.toSet())
    }

    private fun debt(
        status: DebtStatus,
        origin: LocalDate,
        taskId: UUID = UUID.randomUUID(),
        userId: UUID = owner,
    ): TimeDebt = TimeDebt(
        userId = userId,
        taskId = taskId,
        minutes = 60,
        originDate = origin,
        status = status,
    )
}
