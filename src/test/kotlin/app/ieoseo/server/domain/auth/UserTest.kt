package app.ieoseo.server.domain.auth

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * User Aggregate 불변식 테스트(Supabase Auth 전환 후, ADR-0014).
 *
 * id 는 Supabase JWT sub(UUID)로 앱이 주입한다. 자체 provider/passwordHash 개념 없음.
 */
class UserTest {

    private fun user(nickname: String = "지우") =
        User(id = UUID.randomUUID(), email = "a@ieoseo.app", nickname = nickname)

    @Test
    fun `id email nickname 으로 생성되며 기본 상태는 ACTIVE`() {
        val id = UUID.randomUUID()
        val user = User(id = id, email = "a@ieoseo.app", nickname = "지우")

        assertEquals(id, user.id)
        assertEquals(UserStatus.ACTIVE, user.status)
        assertTrue(user.isActive)
    }

    @Test
    fun `email 은 비어 있을 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            User(id = UUID.randomUUID(), email = "  ", nickname = "지우")
        }
    }

    @Test
    fun `nickname 은 비어 있을 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            User(id = UUID.randomUUID(), email = "a@ieoseo.app", nickname = " ")
        }
    }

    @Test
    fun `updateNickname 은 trim 후 닉네임을 바꾼다`() {
        val user = user()

        user.updateNickname("  새이름  ")

        assertEquals("새이름", user.nickname)
    }

    @Test
    fun `updateNickname 은 빈 값이면 거부한다`() {
        assertFailsWith<IllegalArgumentException> { user().updateNickname("   ") }
    }

    @Test
    fun `updateNickname 은 20자를 넘으면 거부한다`() {
        assertFailsWith<IllegalArgumentException> { user().updateNickname("가".repeat(21)) }
    }

    @Test
    fun `withdraw 는 상태를 WITHDRAWN 으로 전이한다`() {
        val user = user()
        assertTrue(user.isActive)

        user.withdraw()

        assertEquals(UserStatus.WITHDRAWN, user.status)
        assertFalse(user.isActive)
    }

    @Test
    fun `withdraw 는 멱등이다`() {
        val user = user()
        user.withdraw()
        user.withdraw()

        assertEquals(UserStatus.WITHDRAWN, user.status)
    }
}
