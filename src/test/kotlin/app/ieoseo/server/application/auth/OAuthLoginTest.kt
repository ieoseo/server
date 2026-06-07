package app.ieoseo.server.application.auth

import app.ieoseo.server.domain.auth.AuthProvider
import app.ieoseo.server.domain.auth.User
import app.ieoseo.server.infrastructure.auth.InMemoryRefreshTokenStore
import app.ieoseo.server.infrastructure.oauth.OAuthIdentity
import app.ieoseo.server.infrastructure.oauth.OAuthInvalidException
import app.ieoseo.server.infrastructure.oauth.OAuthVerifier
import app.ieoseo.server.infrastructure.oauth.OAuthVerifierRegistry
import app.ieoseo.server.infrastructure.persistence.auth.UserRepository
import app.ieoseo.server.infrastructure.security.JwtProperties
import app.ieoseo.server.infrastructure.security.JwtProvider
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AuthService.oauthLogin 단위 테스트 — verifier 는 가짜 구현을 주입한다.
 *
 * 외부 HTTP/JWKS 호출 없이 신규 가입·기존 매핑·LOCAL 충돌(409)·검증 실패(401)를 검증한다.
 * 계약: `docs/05-API/auth.md`(OAUTH_INVALID 401, EMAIL_LINKED_LOCAL 409).
 */
class OAuthLoginTest {

    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val passwordEncoder = BCryptPasswordEncoder()
    private val tokenService = TokenService(
        JwtProvider(
            JwtProperties(
                secret = "test-only-jwt-secret-please-change-32bytes-min",
                accessTtlSeconds = 1800,
                refreshTtlSeconds = 1_209_600,
            ),
        ),
        InMemoryRefreshTokenStore(),
    )

    /** provider 별로 고정 결과/예외를 내는 가짜 verifier. */
    private fun fakeVerifier(
        provider: AuthProvider,
        identity: OAuthIdentity? = null,
    ): OAuthVerifier = object : OAuthVerifier {
        override val provider = provider
        override fun verify(token: String): OAuthIdentity =
            identity ?: throw OAuthInvalidException("invalid token")
    }

    private fun serviceWith(vararg verifiers: OAuthVerifier): AuthService =
        AuthService(userRepository, passwordEncoder, tokenService, OAuthVerifierRegistry(verifiers.toList()))

    @Test
    fun `소셜 로그인은 신규 사용자를 가입시키고 isNew true 를 반환한다`() {
        val identity = OAuthIdentity(AuthProvider.GOOGLE, "google-sub-1", "new@gmail.com")
        `when`(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-1")).thenReturn(null)
        `when`(userRepository.findByEmail("new@gmail.com")).thenReturn(null)
        `when`(userRepository.save(any(User::class.java))).thenAnswer { it.arguments[0] }
        val service = serviceWith(fakeVerifier(AuthProvider.GOOGLE, identity))

        val result = service.oauthLogin(AuthProvider.GOOGLE, "tok")

        assertTrue(result.isNew)
        assertEquals("new@gmail.com", result.auth.user.email)
        assertEquals(AuthProvider.GOOGLE, result.auth.user.provider)
        assertEquals("google-sub-1", result.auth.user.providerId)
        assertNotNull(result.auth.tokens.accessToken.value)
    }

    @Test
    fun `소셜 로그인은 기존 매핑 사용자를 그대로 로그인시키고 isNew false`() {
        val existing = User(
            email = "old@gmail.com",
            nickname = "올드",
            provider = AuthProvider.GOOGLE,
            providerId = "google-sub-2",
        )
        `when`(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-2")).thenReturn(existing)
        val service = serviceWith(
            fakeVerifier(AuthProvider.GOOGLE, OAuthIdentity(AuthProvider.GOOGLE, "google-sub-2", "old@gmail.com")),
        )

        val result = service.oauthLogin(AuthProvider.GOOGLE, "tok")

        assertFalse(result.isNew)
        assertEquals(existing.id, result.auth.user.id)
    }

    @Test
    fun `이메일이 이미 LOCAL 로 존재하면 EmailLinkedLocalException`() {
        val local = User(
            email = "dup@gmail.com",
            nickname = "로컬",
            provider = AuthProvider.LOCAL,
            passwordHash = passwordEncoder.encode("password123"),
        )
        `when`(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-3")).thenReturn(null)
        `when`(userRepository.findByEmail("dup@gmail.com")).thenReturn(local)
        val service = serviceWith(
            fakeVerifier(AuthProvider.GOOGLE, OAuthIdentity(AuthProvider.GOOGLE, "google-sub-3", "dup@gmail.com")),
        )

        assertFailsWith<EmailLinkedLocalException> {
            service.oauthLogin(AuthProvider.GOOGLE, "tok")
        }
    }

    @Test
    fun `검증 실패는 OAuthInvalidException 으로 전파된다`() {
        val service = serviceWith(fakeVerifier(AuthProvider.KAKAO, identity = null))

        assertFailsWith<OAuthInvalidException> {
            service.oauthLogin(AuthProvider.KAKAO, "bad-token")
        }
    }

    @Test
    fun `지원하지 않는 provider 는 OAuthInvalidException`() {
        val service = serviceWith(fakeVerifier(AuthProvider.GOOGLE, OAuthIdentity(AuthProvider.GOOGLE, "x", "x@x")))

        // KAKAO verifier 가 등록되지 않음
        assertFailsWith<OAuthInvalidException> {
            service.oauthLogin(AuthProvider.KAKAO, "tok")
        }
    }
}
