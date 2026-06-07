package app.ieoseo.server.presentation.auth

import app.ieoseo.server.domain.auth.AuthProvider
import app.ieoseo.server.infrastructure.security.AuthPrincipal
import app.ieoseo.server.application.auth.AuthService
import app.ieoseo.server.application.auth.TokenService
import app.ieoseo.server.application.settings.UserSettingsService
import app.ieoseo.server.common.ApiResponse
import app.ieoseo.server.presentation.settings.SettingsResponse
import app.ieoseo.server.presentation.settings.UpdateSettingsRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 이메일 인증 엔드포인트. 계약: `docs/05-API/auth.md`.
 *
 * 공개: signup/login/refresh. 인증 필요: me/logout(SecurityConfig 가드).
 * 모든 응답은 공통 envelope([ApiResponse])로 감싼다.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val tokenService: TokenService,
    private val userSettingsService: UserSettingsService,
) {
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@Valid @RequestBody request: SignupRequest): ApiResponse<AuthResponse> =
        ApiResponse.ok(AuthResponse.from(authService.signup(request.email, request.password, request.nickname)))

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ApiResponse<AuthResponse> =
        ApiResponse.ok(AuthResponse.from(authService.login(request.email, request.password)))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ApiResponse<AuthResponse> =
        ApiResponse.ok(AuthResponse.from(authService.refresh(request.refreshToken)))

    /**
     * 소셜 로그인. `provider` = google|kakao|apple. body `{idToken}`(Google/Apple) 또는
     * `{accessToken}`(Kakao). 응답에 `isNew` 포함. 계약: docs/05-API/auth.md.
     * 미지원 provider·토큰 누락 → 400, 검증 실패 → 401 OAUTH_INVALID, LOCAL 충돌 → 409.
     */
    @PostMapping("/oauth/{provider}")
    fun oauth(
        @PathVariable provider: String,
        @RequestBody request: OAuthRequest,
    ): ApiResponse<OAuthResponse> {
        val authProvider = parseProvider(provider)
        val token = request.token() ?: throw IllegalArgumentException("idToken 또는 accessToken 이 필요합니다")
        return ApiResponse.ok(OAuthResponse.from(authService.oauthLogin(authProvider, token)))
    }

    /** 경로 변수 provider 를 소셜 enum 으로 변환. LOCAL·미지원은 거부(→ 400). */
    private fun parseProvider(raw: String): AuthProvider {
        val provider = runCatching { AuthProvider.valueOf(raw.uppercase()) }.getOrNull()
        require(provider != null && provider != AuthProvider.LOCAL) {
            "지원하지 않는 provider 입니다: $raw"
        }
        return provider
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody request: LogoutRequest) {
        tokenService.revoke(request.refreshToken)
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: AuthPrincipal): ApiResponse<UserResponse> =
        ApiResponse.ok(UserResponse.from(authService.me(principal.userId)))

    /** 프로필(닉네임) 수정(이슈 #56). 인증 필요. 검증 실패 → 400, 사용자 없음 → 404. */
    @PatchMapping("/me")
    fun updateProfile(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ApiResponse<UserResponse> =
        ApiResponse.ok(UserResponse.from(authService.updateNickname(principal.userId, request.nickname)))

    /**
     * 회원 탈퇴(이슈 #56). 인증 필요. 상태 WITHDRAWN(소프트) + 해당 사용자 refresh 전부 폐기 → 204.
     * 이후 refresh 재발급 불가, access 는 자연 만료.
     */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@AuthenticationPrincipal principal: AuthPrincipal) {
        authService.withdraw(principal.userId)
    }

    /** 사용자 설정 조회(이슈 #56). 없으면 기본값을 만들어 반환. 인증 필요. */
    @GetMapping("/me/settings")
    fun getSettings(@AuthenticationPrincipal principal: AuthPrincipal): ApiResponse<SettingsResponse> =
        ApiResponse.ok(SettingsResponse.from(userSettingsService.get(principal.userId)))

    /** 사용자 설정 전체 수정(이슈 #56, PUT). 인증 필요. 검증 실패 → 400. */
    @PutMapping("/me/settings")
    fun updateSettings(
        @AuthenticationPrincipal principal: AuthPrincipal,
        @Valid @RequestBody request: UpdateSettingsRequest,
    ): ApiResponse<SettingsResponse> =
        ApiResponse.ok(SettingsResponse.from(userSettingsService.update(principal.userId, request.toCommand())))
}
