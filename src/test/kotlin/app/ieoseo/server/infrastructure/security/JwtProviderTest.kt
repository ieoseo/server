package app.ieoseo.server.infrastructure.security

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JwtProvider 단위 테스트 — HMAC 서명 발급/검증/만료/위조.
 *
 * 계약: `docs/06-백엔드/인증-도메인.md` §3 (claims: sub=userId, email, jti, exp).
 */
class JwtProviderTest {

    // 32바이트 이상 명백한 test 더미 시크릿(실제 시크릿 아님).
    private val secret = "test-only-jwt-secret-please-change-32bytes-min"
    private val accessTtl = Duration.ofMinutes(30)
    private val refreshTtl = Duration.ofDays(14)

    private fun provider(clock: () -> Instant = Instant::now): JwtProvider =
        JwtProvider(
            JwtProperties(secret = secret, accessTtlSeconds = accessTtl.seconds, refreshTtlSeconds = refreshTtl.seconds),
            clock,
        )

    @Test
    fun `access 토큰 발급 후 검증하면 클레임을 복원한다`() {
        val userId = UUID.randomUUID()
        val sut = provider()

        val token = sut.issueAccess(userId, "jiwoo@ieoseo.app")
        val claims = sut.parse(token.value)

        assertEquals(userId, claims.userId)
        assertEquals("jiwoo@ieoseo.app", claims.email)
        assertEquals(TokenType.ACCESS, claims.type)
        assertNotNull(claims.jti)
    }

    @Test
    fun `refresh 토큰은 jti 와 type 을 담는다`() {
        val userId = UUID.randomUUID()
        val sut = provider()

        val token = sut.issueRefresh(userId, "jiwoo@ieoseo.app")
        val claims = sut.parse(token.value)

        assertEquals(TokenType.REFRESH, claims.type)
        assertEquals(userId, claims.userId)
        assertNotNull(claims.jti)
    }

    @Test
    fun `발급 시마다 jti 는 달라진다`() {
        val userId = UUID.randomUUID()
        val sut = provider()

        val a = sut.parse(sut.issueRefresh(userId, "a@ieoseo.app").value)
        val b = sut.parse(sut.issueRefresh(userId, "a@ieoseo.app").value)

        assertNotEquals(a.jti, b.jti)
    }

    @Test
    fun `access 토큰 만료 시간은 TTL 만큼 미래다`() {
        val now = Instant.parse("2026-06-04T00:00:00Z")
        val sut = provider { now }

        val token = sut.issueAccess(UUID.randomUUID(), "a@ieoseo.app")

        assertEquals(accessTtl.seconds, token.expiresInSeconds)
        assertTrue(token.expiresAt.isAfter(now))
    }

    @Test
    fun `만료된 토큰 검증은 실패한다`() {
        val past = Instant.parse("2020-01-01T00:00:00Z")
        val issuer = provider { past }
        val expired = issuer.issueAccess(UUID.randomUUID(), "a@ieoseo.app")

        val verifier = provider() // 현재 시각 기준

        assertFailsWith<InvalidTokenException> { verifier.parse(expired.value) }
    }

    @Test
    fun `다른 시크릿으로 서명된 토큰은 위조로 거부한다`() {
        val attacker = JwtProvider(
            JwtProperties(
                secret = "another-totally-different-secret-key-32bytes!!",
                accessTtlSeconds = accessTtl.seconds,
                refreshTtlSeconds = refreshTtl.seconds,
            ),
        )
        val forged = attacker.issueAccess(UUID.randomUUID(), "evil@ieoseo.app")

        assertFailsWith<InvalidTokenException> { provider().parse(forged.value) }
    }

    @Test
    fun `손상된 토큰 문자열은 거부한다`() {
        assertFailsWith<InvalidTokenException> { provider().parse("not.a.jwt") }
    }
}
