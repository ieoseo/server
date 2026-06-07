package app.ieoseo.server.presentation.auth

import app.ieoseo.server.infrastructure.security.AuthPrincipal
import app.ieoseo.server.application.auth.AuthService
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 프로필·설정 엔드포인트. 계약: `docs/05-API/auth.md`.
 *
 * 인증·토큰 발급은 Supabase Auth + Resource Server 가 담당(ADR-0014). 이 컨트롤러는
 * 인증된 주체(AuthPrincipal)의 프로필(/me)·설정(/me/settings)만 다룬다. 전부 인증 필요.
 * 모든 응답은 공통 envelope([ApiResponse])로 감싼다.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val userSettingsService: UserSettingsService,
) {
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
