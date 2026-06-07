package app.ieoseo.server.domain.auth

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * User Aggregate 불변식 테스트.
 *
 * 계약: `docs/06-백엔드/인증-도메인.md` §1
 * - LOCAL 이면 passwordHash != null
 * - 소셜(provider≠LOCAL)이면 providerId != null
 */
class UserTest {

    @Test
    fun `LOCAL 사용자는 passwordHash 가 필요하다`() {
        assertFailsWith<IllegalArgumentException> {
            User(
                email = "a@ieoseo.app",
                nickname = "지우",
                provider = AuthProvider.LOCAL,
                passwordHash = null,
            )
        }
    }

    @Test
    fun `LOCAL 사용자는 passwordHash 가 있으면 생성된다`() {
        val user = User(
            email = "a@ieoseo.app",
            nickname = "지우",
            provider = AuthProvider.LOCAL,
            passwordHash = "\$2a\$10\$hash",
        )

        assertEquals(AuthProvider.LOCAL, user.provider)
        assertEquals(UserStatus.ACTIVE, user.status)
        assertNull(user.providerId)
    }

    @Test
    fun `소셜 사용자는 providerId 가 필요하다`() {
        assertFailsWith<IllegalArgumentException> {
            User(
                email = "a@ieoseo.app",
                nickname = "지우",
                provider = AuthProvider.GOOGLE,
                providerId = null,
            )
        }
    }

    @Test
    fun `소셜 사용자는 passwordHash 없이 providerId 로 생성된다`() {
        val user = User(
            email = "a@ieoseo.app",
            nickname = "지우",
            provider = AuthProvider.GOOGLE,
            providerId = "google-sub-123",
        )

        assertEquals(AuthProvider.GOOGLE, user.provider)
        assertNull(user.passwordHash)
        assertEquals("google-sub-123", user.providerId)
    }

    @Test
    fun `email 은 비어 있을 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            User(
                email = "  ",
                nickname = "지우",
                provider = AuthProvider.LOCAL,
                passwordHash = "\$2a\$10\$hash",
            )
        }
    }

    @Test
    fun `nickname 은 비어 있을 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            User(
                email = "a@ieoseo.app",
                nickname = " ",
                provider = AuthProvider.LOCAL,
                passwordHash = "\$2a\$10\$hash",
            )
        }
    }

    private fun localUser(nickname: String = "지우") = User(
        email = "a@ieoseo.app",
        nickname = nickname,
        provider = AuthProvider.LOCAL,
        passwordHash = "\$2a\$10\$hash",
    )

    @Test
    fun `updateNickname 은 trim 후 닉네임을 바꾼다`() {
        val user = localUser()

        user.updateNickname("  새이름  ")

        assertEquals("새이름", user.nickname)
    }

    @Test
    fun `updateNickname 은 빈 값이면 거부한다`() {
        val user = localUser()

        assertFailsWith<IllegalArgumentException> { user.updateNickname("   ") }
    }

    @Test
    fun `updateNickname 은 20자를 넘으면 거부한다`() {
        val user = localUser()

        assertFailsWith<IllegalArgumentException> { user.updateNickname("가".repeat(21)) }
    }

    @Test
    fun `withdraw 는 상태를 WITHDRAWN 으로 전이한다`() {
        val user = localUser()
        assertEquals(true, user.isActive)

        user.withdraw()

        assertEquals(UserStatus.WITHDRAWN, user.status)
        assertEquals(false, user.isActive)
    }

    @Test
    fun `withdraw 는 멱등이다`() {
        val user = localUser()
        user.withdraw()
        user.withdraw()

        assertEquals(UserStatus.WITHDRAWN, user.status)
    }
}
