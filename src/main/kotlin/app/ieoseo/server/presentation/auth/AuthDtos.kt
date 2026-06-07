package app.ieoseo.server.presentation.auth

import app.ieoseo.server.domain.auth.AuthProvider
import app.ieoseo.server.domain.auth.User
import app.ieoseo.server.application.auth.AuthResult
import app.ieoseo.server.application.auth.OAuthLoginResult
import app.ieoseo.server.application.auth.TokenPair
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

/** 이메일 회원가입 요청. 검증: 이메일 형식·비밀번호 8자+·닉네임 1~20자. */
data class SignupRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 8, max = 100) val password: String,
    @field:NotBlank @field:Size(min = 1, max = 20) val nickname: String,
)

/** 이메일 로그인 요청. */
data class LoginRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
)

/** 토큰 재발급 요청. */
data class RefreshRequest(
    @field:NotBlank val refreshToken: String,
)

/** 로그아웃 요청(해당 기기 refresh 폐기). */
data class LogoutRequest(
    @field:NotBlank val refreshToken: String,
)

/** 프로필 수정 요청(닉네임, 이슈 #56). 검증: 1~20자. */
data class UpdateProfileRequest(
    @field:NotBlank @field:Size(min = 1, max = 20) val nickname: String,
)

/**
 * 소셜 로그인 요청. provider 별로 둘 중 하나를 채운다.
 * - Google/Apple: [idToken](ID token).
 * - Kakao: [accessToken].
 * 둘 다 비면 검증 실패(400). 계약: docs/05-API/auth.md.
 */
data class OAuthRequest(
    val idToken: String? = null,
    val accessToken: String? = null,
) {
    /** provider 토큰 값(idToken 우선, 없으면 accessToken). 둘 다 없으면 null. */
    fun token(): String? = idToken?.takeIf { it.isNotBlank() } ?: accessToken?.takeIf { it.isNotBlank() }
}

/** 소셜 로그인 응답(사용자 + 토큰 + 신규 가입 여부). 계약: docs/05-API/auth.md. */
data class OAuthResponse(
    val user: UserResponse,
    val tokens: TokenResponse,
    val isNew: Boolean,
) {
    companion object {
        fun from(result: OAuthLoginResult): OAuthResponse = OAuthResponse(
            user = UserResponse.from(result.auth.user),
            tokens = TokenResponse.from(result.auth.tokens),
            isNew = result.isNew,
        )
    }
}

/** 사용자 공개 표현. 계약: docs/05-API/auth.md(id·email·nickname·provider). */
data class UserResponse(
    val id: UUID,
    val email: String,
    val nickname: String,
    val provider: AuthProvider,
) {
    companion object {
        fun from(user: User): UserResponse =
            UserResponse(id = user.id, email = user.email, nickname = user.nickname, provider = user.provider)
    }
}

/** 토큰 응답. 계약: docs/05-API/auth.md(accessToken·refreshToken·tokenType·expiresIn). */
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
) {
    companion object {
        const val BEARER = "Bearer"

        fun from(tokens: TokenPair): TokenResponse = TokenResponse(
            accessToken = tokens.accessToken.value,
            refreshToken = tokens.refreshToken.value,
            tokenType = BEARER,
            expiresIn = tokens.accessToken.expiresInSeconds,
        )
    }
}

/** 인증 성공 응답(사용자 + 토큰). signup/login/refresh 공통. */
data class AuthResponse(
    val user: UserResponse,
    val tokens: TokenResponse,
) {
    companion object {
        fun from(result: AuthResult): AuthResponse =
            AuthResponse(user = UserResponse.from(result.user), tokens = TokenResponse.from(result.tokens))
    }
}
