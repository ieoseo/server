package app.ieoseo.server.calendar.oauth

import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.calendar.domain.ConnectionStatus
import app.ieoseo.server.calendar.repository.CalendarConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * 서버 주도 Google Calendar OAuth 오케스트레이션(이슈 #9).
 *
 * 1) [buildGoogleAuthUrl] — 사용자별 state 발급 후 동의 URL 생성(앱이 브라우저로 연다).
 * 2) [handleGoogleCallback] — Google 콜백의 code/state 를 받아 토큰 교환·연결 저장 후
 *    앱으로 복귀할 딥링크를 돌려준다. 콜백은 브라우저 리다이렉트이므로 예외를 던지지 않고
 *    항상 status 가 담긴 딥링크를 반환한다(실패 사유는 로그로 남긴다).
 */
@Service
class CalendarOAuthService(
    private val properties: GoogleOAuthProperties,
    private val stateStore: OAuthStateStore,
    private val oauthClient: GoogleOAuthClient,
    private val connectionRepository: CalendarConnectionRepository,
) {
    private val log = LoggerFactory.getLogger(CalendarOAuthService::class.java)

    /** Google 동의 URL 생성. 설정 미완료면 명확히 실패(로그 + 예외). */
    fun buildGoogleAuthUrl(userId: UUID): String {
        if (!properties.isConfigured) {
            log.error("Google 캘린더 OAuth 미설정(GOOGLE_CLIENT_ID 등 누락) — 연동 시작 불가")
            throw GoogleOAuthException("Google 캘린더 연동이 설정되지 않았습니다")
        }
        val state = stateStore.issue(userId)
        val params = mapOf(
            "client_id" to properties.clientId,
            "redirect_uri" to properties.redirectUri,
            "response_type" to "code",
            "scope" to properties.scope,
            "access_type" to "offline",
            "include_granted_scopes" to "true",
            "prompt" to "consent",
            "state" to state,
        )
        val query = params.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        log.info("Google 캘린더 OAuth 시작 user={}", userId)
        return "${properties.authUri}?$query"
    }

    /** Google 콜백 처리 → 앱 복귀 딥링크(항상 반환). 실패 사유는 로그로 남긴다. */
    @Transactional
    fun handleGoogleCallback(code: String?, state: String?, error: String?): String {
        if (!error.isNullOrBlank()) {
            log.warn("Google 캘린더 동의 거부/오류: {}", error)
            return returnDeeplink("error", "consent_denied")
        }
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            log.warn("Google 캘린더 콜백 파라미터 누락(code/state)")
            return returnDeeplink("error", "missing_params")
        }
        val userId = stateStore.consume(state)
        if (userId == null) {
            log.warn("Google 캘린더 콜백 state 무효/만료")
            return returnDeeplink("error", "invalid_state")
        }
        return try {
            val tokens = oauthClient.exchangeCode(code)
            upsertConnection(userId, tokens)
            log.info("Google 캘린더 연동 성공 user={} hasRefreshToken={}", userId, tokens.refreshToken != null)
            returnDeeplink("success", null)
        } catch (ex: GoogleOAuthException) {
            log.warn("Google 캘린더 토큰 교환 실패 user={}: {}", userId, ex.message)
            returnDeeplink("error", "token_exchange_failed")
        }
    }

    private fun upsertConnection(userId: UUID, tokens: GoogleTokens) {
        val existing = connectionRepository
            .findByUserIdAndProvider(userId, CalendarProvider.GOOGLE)
            .orElse(null)
        if (existing != null) {
            existing.accessToken = tokens.accessToken
            // 재동의에서 refresh token 이 안 오면(null) 기존 값을 보존한다.
            if (tokens.refreshToken != null) existing.refreshToken = tokens.refreshToken
            existing.expiresAt = tokens.expiresAt
            existing.status = ConnectionStatus.CONNECTED
            connectionRepository.save(existing)
            return
        }
        connectionRepository.save(
            CalendarConnection(
                userId = userId,
                provider = CalendarProvider.GOOGLE,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresAt = tokens.expiresAt,
                status = ConnectionStatus.CONNECTED,
            ),
        )
    }

    private fun returnDeeplink(status: String, reason: String?): String {
        val base = "${properties.returnDeeplink}?status=$status"
        return if (reason != null) "$base&reason=$reason" else base
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
