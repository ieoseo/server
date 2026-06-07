package app.ieoseo.server.application.auth

import app.ieoseo.server.domain.auth.AuthProvider
import app.ieoseo.server.domain.auth.User
import app.ieoseo.server.infrastructure.oauth.OAuthIdentity
import app.ieoseo.server.infrastructure.oauth.OAuthVerifierRegistry
import app.ieoseo.server.infrastructure.persistence.auth.UserRepository
import app.ieoseo.server.common.NotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 인증 결과(사용자 + 발급 토큰). controller 가 응답 DTO 로 변환한다.
 */
data class AuthResult(
    val user: User,
    val tokens: TokenPair,
)

/**
 * 소셜 로그인 결과. [isNew] 는 이번 요청으로 신규 가입되었는지 여부(응답 `isNew`).
 */
data class OAuthLoginResult(
    val auth: AuthResult,
    val isNew: Boolean,
)

/**
 * 이메일 인증 서비스. 계약: `docs/05-API/auth.md`, `docs/06-백엔드/인증-도메인.md`.
 *
 * - [signup]: 이메일 중복 검사(→ [EmailTakenException]) → BCrypt 해시 → 저장 → 토큰 발급.
 * - [login]: 이메일 조회 + 해시 검증(실패 시 [InvalidCredentialsException], 원인 비구분) → 토큰 발급.
 * - [me]: userId 로 현재 사용자 조회(없으면 [NotFoundException]).
 *
 * 비밀번호 평문은 로깅하지 않으며 해시만 저장한다.
 */
@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val oAuthVerifierRegistry: OAuthVerifierRegistry,
) {
    @Transactional
    fun signup(email: String, rawPassword: String, nickname: String): AuthResult {
        val normalizedEmail = email.trim().lowercase()
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw EmailTakenException(normalizedEmail)
        }

        val user = User(
            email = normalizedEmail,
            nickname = nickname.trim(),
            provider = AuthProvider.LOCAL,
            passwordHash = passwordEncoder.encode(rawPassword),
        )
        val saved = userRepository.save(user)
        return AuthResult(saved, tokenService.issue(saved))
    }

    fun login(email: String, rawPassword: String): AuthResult {
        val normalizedEmail = email.trim().lowercase()
        val user = userRepository.findByEmail(normalizedEmail) ?: throw InvalidCredentialsException()
        val hash = user.passwordHash ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(rawPassword, hash)) {
            throw InvalidCredentialsException()
        }
        return AuthResult(user, tokenService.issue(user))
    }

    /**
     * 소셜 로그인. 계약: `docs/05-API/auth.md`(OAUTH_INVALID 401, EMAIL_LINKED_LOCAL 409).
     *
     * 1) provider verifier 로 토큰 검증([app.ieoseo.server.infrastructure.oauth.OAuthInvalidException]).
     * 2) `(provider, providerId)` 로 기존 사용자 조회 → 있으면 로그인(isNew=false).
     * 3) 없으면: 이메일이 이미 LOCAL 로 존재하면 연결 거부([EmailLinkedLocalException]).
     *    아니면 소셜 계정으로 신규 가입(isNew=true).
     * 4) 자체 JWT(access+refresh) 발급.
     */
    @Transactional
    fun oauthLogin(provider: AuthProvider, token: String): OAuthLoginResult {
        val identity = oAuthVerifierRegistry.forProvider(provider).verify(token)

        val existing = userRepository.findByProviderAndProviderId(identity.provider, identity.providerId)
        if (existing != null) {
            return OAuthLoginResult(AuthResult(existing, tokenService.issue(existing)), isNew = false)
        }

        val email = identity.email.trim().lowercase()
        userRepository.findByEmail(email)?.let { conflicting ->
            if (conflicting.provider == AuthProvider.LOCAL) {
                throw EmailLinkedLocalException(email)
            }
        }

        val created = userRepository.save(
            User(
                email = email,
                nickname = defaultNickname(email),
                provider = identity.provider,
                providerId = identity.providerId,
            ),
        )
        return OAuthLoginResult(AuthResult(created, tokenService.issue(created)), isNew = true)
    }

    /** 소셜 가입 시 기본 닉네임 — 이메일 로컬파트를 20자 이내로 사용한다(사용자가 추후 변경). */
    private fun defaultNickname(email: String): String =
        email.substringBefore('@').take(NICKNAME_MAX_LENGTH).ifBlank { "user" }

    /**
     * refresh 토큰 회전 후 새 토큰 + 사용자 정보를 함께 반환한다(envelope 일관성).
     * 만료·폐기·재사용·위조 시 [app.ieoseo.server.infrastructure.security.InvalidTokenException].
     */
    fun refresh(refreshToken: String): AuthResult {
        val tokens = tokenService.rotate(refreshToken)
        val user = userRepository.findById(tokens.userId)
            .orElseThrow { NotFoundException("User", tokens.userId) }
        return AuthResult(user, tokens)
    }

    fun me(userId: UUID): User =
        userRepository.findById(userId).orElseThrow { NotFoundException("User", userId) }

    /**
     * 프로필 닉네임 수정(이슈 #56, `PATCH /auth/me`). 사용자 조회 후 [User.updateNickname]
     * (trim·길이 방어)로 갱신한다. 없으면 [NotFoundException].
     */
    @Transactional
    fun updateNickname(userId: UUID, nickname: String): User {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User", userId) }
        user.updateNickname(nickname)
        return user
    }

    /**
     * 회원 탈퇴(이슈 #56, `DELETE /auth/me`, 인증-도메인 §3). 소프트 삭제:
     * 상태를 WITHDRAWN 으로 전이([User.withdraw])하고 **해당 사용자의 모든 refresh 토큰을 폐기**한다.
     * 이후 refresh 재발급은 불가하며 access 는 짧은 TTL 로 자연 만료된다. 없으면 [NotFoundException].
     */
    @Transactional
    fun withdraw(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User", userId) }
        user.withdraw()
        tokenService.revokeAll(userId)
    }

    private companion object {
        const val NICKNAME_MAX_LENGTH = 20
    }
}
