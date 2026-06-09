package app.ieoseo.server.user.service

import app.ieoseo.server.global.exception.NotFoundException
import app.ieoseo.server.user.domain.User
import app.ieoseo.server.user.domain.UserStatus
import app.ieoseo.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * AuthService 단위 테스트(Supabase Auth 전환 후, ADR-0014) — UserRepository mock.
 *
 * - me: 존재하면 반환, 없으면 NotFound.
 * - updateNickname: 닉네임 갱신(영속 컨텍스트 dirty checking 가정, 도메인 메서드 호출 검증).
 * - withdraw: status 를 WITHDRAWN 으로 전이.
 */
class AuthServiceTest {

    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val service = AuthService(userRepository)
    private val userId = UUID.randomUUID()

    private fun user(nickname: String = "지우") =
        User(id = userId, email = "jiwoo@ieoseo.app", nickname = nickname)

    @Test
    fun `me 는 존재하는 사용자를 반환한다`() {
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(user()))

        val found = service.me(userId)

        assertEquals(userId, found.id)
        assertEquals("지우", found.nickname)
    }

    @Test
    fun `me 는 없는 사용자면 NotFound`() {
        `when`(userRepository.findById(userId)).thenReturn(Optional.empty())

        assertFailsWith<NotFoundException> { service.me(userId) }
    }

    @Test
    fun `updateNickname 은 닉네임을 갱신한다`() {
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(user()))

        val updated = service.updateNickname(userId, "새이름")

        assertEquals("새이름", updated.nickname)
    }

    @Test
    fun `updateNickname 은 없는 사용자면 NotFound`() {
        `when`(userRepository.findById(userId)).thenReturn(Optional.empty())

        assertFailsWith<NotFoundException> { service.updateNickname(userId, "새이름") }
    }

    @Test
    fun `withdraw 는 상태를 WITHDRAWN 으로 전이한다`() {
        val user = user()
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(user))

        service.withdraw(userId)

        assertEquals(UserStatus.WITHDRAWN, user.status)
    }

    @Test
    fun `withdraw 는 없는 사용자면 NotFound`() {
        `when`(userRepository.findById(userId)).thenReturn(Optional.empty())

        assertFailsWith<NotFoundException> { service.withdraw(userId) }
    }
}
