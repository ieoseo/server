package app.ieoseo.server.application.auth

import app.ieoseo.server.domain.auth.AuthProvider
import app.ieoseo.server.domain.auth.User
import app.ieoseo.server.infrastructure.auth.InMemoryRefreshTokenStore
import app.ieoseo.server.infrastructure.security.InvalidTokenException
import app.ieoseo.server.infrastructure.security.JwtProperties
import app.ieoseo.server.infrastructure.security.JwtProvider
import app.ieoseo.server.infrastructure.security.TokenType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * TokenService 단위 테스트 — access+refresh 발급, refresh 회전, 이전 jti 폐기, 재사용 감지.
 *
 * 계약: `docs/06-백엔드/인증-도메인.md` §3 (회전·재사용 감지 시 전체 폐기).
 * Redis 없이 in-memory RefreshTokenStore 로 검증한다.
 */
class TokenServiceTest {

    private val jwtProvider = JwtProvider(
        JwtProperties(
            secret = "test-only-jwt-secret-please-change-32bytes-min",
            accessTtlSeconds = 1800,
            refreshTtlSeconds = 1_209_600,
        ),
    )

    private fun newService(): Pair<TokenService, InMemoryRefreshTokenStore> {
        val store = InMemoryRefreshTokenStore()
        return TokenService(jwtProvider, store) to store
    }

    private fun user(): User = User(
        email = "jiwoo@ieoseo.app",
        nickname = "지우",
        provider = AuthProvider.LOCAL,
        passwordHash = "\$2a\$10\$hash",
    )

    @Test
    fun `토큰 발급 시 access 와 refresh 를 내고 refresh jti 를 저장한다`() {
        val (service, store) = newService()
        val u = user()

        val pair = service.issue(u)

        assertEquals(TokenType.ACCESS, jwtProvider.parse(pair.accessToken.value).type)
        val refreshJti = jwtProvider.parse(pair.refreshToken.value).jti
        assertTrue(store.exists(u.id, refreshJti))
    }

    @Test
    fun `회전하면 새 토큰을 내고 이전 refresh jti 를 폐기한다`() {
        val (service, store) = newService()
        val u = user()
        val first = service.issue(u)
        val oldJti = jwtProvider.parse(first.refreshToken.value).jti

        val rotated = service.rotate(first.refreshToken.value)

        val newJti = jwtProvider.parse(rotated.refreshToken.value).jti
        assertNotEquals(oldJti, newJti)
        assertTrue(store.exists(u.id, newJti))
        assertTrue(!store.exists(u.id, oldJti)) // 이전 jti 폐기됨
    }

    @Test
    fun `폐기된 refresh 재사용은 거부하고 해당 사용자 전체를 폐기한다`() {
        val (service, store) = newService()
        val u = user()
        val first = service.issue(u)
        // 1회 회전 → first 의 jti 는 폐기됨
        val rotated = service.rotate(first.refreshToken.value)
        val rotatedJti = jwtProvider.parse(rotated.refreshToken.value).jti

        // 폐기된 first 토큰을 재사용 시도(재사용 감지)
        assertFailsWith<InvalidTokenException> { service.rotate(first.refreshToken.value) }

        // 재사용 감지 시 사용자 전체 폐기 → 정상 발급분도 무효화
        assertTrue(!store.exists(u.id, rotatedJti))
    }

    @Test
    fun `access 토큰으로 회전 시도는 거부한다`() {
        val (service, _) = newService()
        val u = user()
        val pair = service.issue(u)

        assertFailsWith<InvalidTokenException> { service.rotate(pair.accessToken.value) }
    }

    @Test
    fun `위조된 refresh 는 거부한다`() {
        val (service, _) = newService()

        assertFailsWith<InvalidTokenException> { service.rotate("not.a.jwt") }
    }

    @Test
    fun `로그아웃은 해당 refresh jti 만 폐기한다`() {
        val (service, store) = newService()
        val u = user()
        val pair = service.issue(u)
        val jti = jwtProvider.parse(pair.refreshToken.value).jti

        service.revoke(pair.refreshToken.value)

        assertTrue(!store.exists(u.id, jti))
    }
}
