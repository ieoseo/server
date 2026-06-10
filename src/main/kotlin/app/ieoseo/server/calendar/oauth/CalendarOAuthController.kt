package app.ieoseo.server.calendar.oauth

import app.ieoseo.server.global.common.ApiResponse
import app.ieoseo.server.global.security.AuthPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** 동의 URL 응답. 앱이 이 URL 을 외부 브라우저로 연다. */
data class GoogleAuthUrlResponse(val url: String)

/**
 * 서버 주도 Google Calendar OAuth 엔드포인트(이슈 #9).
 *
 * - `GET /connect/google/url` — 인증 필요. 동의 URL 을 발급한다(앱이 브라우저로 연다).
 * - `GET /oauth/google/callback` — **공개**(Google 브라우저 리다이렉트). SecurityConfig 에서 permitAll.
 *   처리 후 앱 딥링크로 302 리다이렉트한다(성공/실패 status 포함). 사용자 식별은 state(OAuthStateStore).
 */
@RestController
@RequestMapping("/api/v1/calendar")
class CalendarOAuthController(
    private val oauthService: CalendarOAuthService,
) {
    @GetMapping("/connect/google/url")
    fun googleAuthUrl(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ApiResponse<GoogleAuthUrlResponse> =
        ApiResponse.ok(GoogleAuthUrlResponse(oauthService.buildGoogleAuthUrl(principal.userId)))

    @GetMapping("/oauth/google/callback")
    fun googleCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> {
        val deeplink = oauthService.handleGoogleCallback(code, state, error)
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(deeplink)).build()
    }
}
