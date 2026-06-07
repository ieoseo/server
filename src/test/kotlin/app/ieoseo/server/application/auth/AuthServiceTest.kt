package app.ieoseo.server.application.auth

import app.ieoseo.server.domain.auth.AuthProvider
import app.ieoseo.server.domain.auth.User
import app.ieoseo.server.infrastructure.auth.InMemoryRefreshTokenStore
import app.ieoseo.server.infrastructure.oauth.OAuthVerifierRegistry
import app.ieoseo.server.infrastructure.persistence.auth.UserRepository
import app.ieoseo.server.infrastructure.security.JwtProperties
import app.ieoseo.server.infrastructure.security.JwtProvider
import app.ieoseo.server.common.NotFoundException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AuthService 단위 테스트 — 가입 시 BCrypt 해시·중복 거부, 로그인 검증·실패.
 *
 * 계약: `docs/05-API/auth.md`(EMAIL_TAKEN 409, INVALID_CREDENTIALS 401).
 * Repository 는 mock, BCrypt 인코더와 in-memory 토큰 스토어는 실제 구현을 쓴다.
 */
class AuthServiceTest {

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

    private val service = AuthService(
        userRepository,
        passwordEncoder,
        tokenService,
        OAuthVerifierRegistry(emptyList()),
    )

    @Test
    fun `회원가입은 비밀번호를 BCrypt 로 해시하고 토큰을 발급한다`() {
        `when`(userRepository.existsByEmail("jiwoo@ieoseo.app")).thenReturn(false)
        `when`(userRepository.save(any(User::class.java))).thenAnswer { it.arguments[0] }

        val result = service.signup("jiwoo@ieoseo.app", "password123", "지우")

        assertEquals("jiwoo@ieoseo.app", result.user.email)
        assertEquals(AuthProvider.LOCAL, result.user.provider)
        // 평문이 그대로 저장되지 않고 BCrypt 검증으로만 매칭된다
        val hash = result.user.passwordHash
        assertNotNull(hash)
        assertTrue(passwordEncoder.matches("password123", hash))
        assertNotNull(result.tokens.accessToken.value)
        assertNotNull(result.tokens.refreshToken.value)
    }

    @Test
    fun `중복 이메일 가입은 EmailTakenException 을 던진다`() {
        `when`(userRepository.existsByEmail("taken@ieoseo.app")).thenReturn(true)

        assertFailsWith<EmailTakenException> {
            service.signup("taken@ieoseo.app", "password123", "지우")
        }
    }

    @Test
    fun `로그인은 올바른 비밀번호면 토큰을 발급한다`() {
        val hashed = passwordEncoder.encode("password123")
        val user = User(
            email = "jiwoo@ieoseo.app",
            nickname = "지우",
            provider = AuthProvider.LOCAL,
            passwordHash = hashed,
        )
        `when`(userRepository.findByEmail("jiwoo@ieoseo.app")).thenReturn(user)

        val result = service.login("jiwoo@ieoseo.app", "password123")

        assertEquals(user.id, result.user.id)
        assertNotNull(result.tokens.accessToken.value)
    }

    @Test
    fun `로그인은 비밀번호가 틀리면 InvalidCredentialsException`() {
        val user = User(
            email = "jiwoo@ieoseo.app",
            nickname = "지우",
            provider = AuthProvider.LOCAL,
            passwordHash = passwordEncoder.encode("password123"),
        )
        `when`(userRepository.findByEmail("jiwoo@ieoseo.app")).thenReturn(user)

        assertFailsWith<InvalidCredentialsException> {
            service.login("jiwoo@ieoseo.app", "wrong-password")
        }
    }

    @Test
    fun `로그인은 없는 이메일이면 InvalidCredentialsException`() {
        `when`(userRepository.findByEmail("nobody@ieoseo.app")).thenReturn(null)

        assertFailsWith<InvalidCredentialsException> {
            service.login("nobody@ieoseo.app", "password123")
        }
    }

    @Test
    fun `me 는 userId 로 사용자를 조회한다`() {
        val user = User(
            email = "jiwoo@ieoseo.app",
            nickname = "지우",
            provider = AuthProvider.LOCAL,
            passwordHash = passwordEncoder.encode("password123"),
        )
        `when`(userRepository.findById(user.id)).thenReturn(Optional.of(user))

        val found = service.me(user.id)

        assertEquals("jiwoo@ieoseo.app", found.email)
    }

    @Test
    fun `me 는 없는 사용자면 NotFoundException`() {
        val id = UUID.randomUUID()
        `when`(userRepository.findById(id)).thenReturn(Optional.empty())

        assertFailsWith<NotFoundException> { service.me(id) }
    }

    @Test
    fun `updateNickname 은 사용자 닉네임을 바꾼다`() {
        val user = User(
            email = "jiwoo@ieoseo.app",
            nickname = "지우",
            provider = AuthProvider.LOCAL,
            passwordHash = passwordEncoder.encode("password123"),
        )
        `when`(userRepository.findById(user.id)).thenReturn(Optional.of(user))

        val updated = service.updateNickname(user.id, "새이름")

        assertEquals("새이름", updated.nickname)
    }

    @Test
    fun `updateNickname 은 없는 사용자면 NotFoundException`() {
        val id = UUID.randomUUID()
        `when`(userRepository.findById(id)).thenReturn(Optional.empty())

        assertFailsWith<NotFoundException> { service.updateNickname(id, "새이름") }
    }

    @Test
    fun `withdraw 는 상태를 WITHDRAWN 으로 바꾸고 refresh 를 전부 폐기한다`() {
        val user = User(
            email = "jiwoo@ieoseo.app",
            nickname = "지우",
            provider = AuthProvider.LOCAL,
            passwordHash = passwordEncoder.encode("password123"),
        )
        // 탈퇴 전 발급된 refresh 가 살아 있다가, 탈퇴 후 무효가 되어야 한다.
        val issued = tokenService.issue(user)
        `when`(userRepository.findById(user.id)).thenReturn(Optional.of(user))

        service.withdraw(user.id)

        assertEquals(app.ieoseo.server.domain.auth.UserStatus.WITHDRAWN, user.status)
        // 폐기된 refresh 로 회전 시도 → 재사용/폐기 감지로 InvalidTokenException.
        assertFailsWith<app.ieoseo.server.infrastructure.security.InvalidTokenException> {
            tokenService.rotate(issued.refreshToken.value)
        }
    }

    @Test
    fun `withdraw 는 없는 사용자면 NotFoundException`() {
        val id = UUID.randomUUID()
        `when`(userRepository.findById(id)).thenReturn(Optional.empty())

        assertFailsWith<NotFoundException> { service.withdraw(id) }
    }
}
