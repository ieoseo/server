package app.ieoseo.server.debt.service

import app.ieoseo.server.user.domain.User
import app.ieoseo.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

/**
 * 자정 부채 생성 잡 오케스트레이션 단위 테스트 (#55, B-2).
 *
 * 사용자별 생성은 [UserDebtGenerator] 가 담당하므로 여기서는 **전 사용자 순회·합산·격리**만 검증한다.
 * 한 사용자 처리가 예외로 실패해도 잡은 다음 사용자로 계속 진행해야 한다(전 사용자 단일 트랜잭션 제거).
 */
class DebtGenerationServiceTest {

    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val userDebtGenerator: UserDebtGenerator = mock(UserDebtGenerator::class.java)

    private val service = DebtGenerationService(userRepository, userDebtGenerator)

    private val today = LocalDate.of(2026, 6, 5) // 금요일
    private val yesterday = today.minusDays(1)

    @Test
    fun `여러 사용자를 순회하며 각자 생성 건수를 합산한다`() {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()
        `when`(userRepository.findAll()).thenReturn(listOf(user(userA), user(userB)))
        `when`(userDebtGenerator.generate(userA, yesterday)).thenReturn(1)
        `when`(userDebtGenerator.generate(userB, yesterday)).thenReturn(2)

        val created = service.run(today)

        assertEquals(3, created)
        verify(userDebtGenerator).generate(userA, yesterday)
        verify(userDebtGenerator).generate(userB, yesterday)
    }

    @Test
    fun `한 사용자 생성이 실패해도 다음 사용자를 계속 처리한다`() {
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()
        `when`(userRepository.findAll()).thenReturn(listOf(user(userA), user(userB)))
        `when`(userDebtGenerator.generate(userA, yesterday)).thenThrow(RuntimeException("DB 오류"))
        `when`(userDebtGenerator.generate(userB, yesterday)).thenReturn(2)

        val created = service.run(today)

        assertEquals(2, created) // 실패한 A 는 0, B 는 2
        verify(userDebtGenerator).generate(userB, yesterday)
    }

    @Test
    fun `사용자가 없으면 0 을 반환한다`() {
        `when`(userRepository.findAll()).thenReturn(emptyList())

        val created = service.run(today)

        assertEquals(0, created)
    }

    private fun user(id: UUID): User = User(
        id = id,
        email = "u$id@example.com",
        nickname = "n",
    )
}
