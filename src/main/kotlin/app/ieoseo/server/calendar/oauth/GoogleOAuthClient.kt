package app.ieoseo.server.calendar.oauth

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

/** Google OAuth 토큰 교환/갱신 결과. refreshToken 은 최초 교환(consent)에서만 내려올 수 있다. */
data class GoogleTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Instant,
)

/** Google OAuth 처리 실패(코드 교환·토큰 갱신·설정 누락). 메시지는 비민감 정보만. */
class GoogleOAuthException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Google OAuth 토큰 엔드포인트 호출 추상화(이슈 #9). 테스트는 가짜를 주입한다.
 * 실패(비2xx·전송 오류·파싱 실패)는 [GoogleOAuthException] 으로 던지며, 토큰 값은 로깅하지 않는다.
 */
interface GoogleOAuthClient {
    /** authorization code → access/refresh 토큰 교환. */
    fun exchangeCode(code: String): GoogleTokens

    /** refresh token 으로 access token 재발급(refresh token 은 보통 유지). */
    fun refresh(refreshToken: String): GoogleTokens
}

/**
 * 실제 Google 토큰 엔드포인트 호출 구현. form-urlencoded POST, JSON 응답 파싱.
 * 모든 실패 지점에 진단 로그를 남긴다(상태코드/오류코드만, 토큰·시크릿 제외).
 */
@Component
class HttpGoogleOAuthClient(
    private val properties: GoogleOAuthProperties,
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .build(),
) : GoogleOAuthClient {

    private val log = LoggerFactory.getLogger(HttpGoogleOAuthClient::class.java)

    override fun exchangeCode(code: String): GoogleTokens {
        val form = mapOf(
            "code" to code,
            "client_id" to properties.clientId,
            "client_secret" to properties.clientSecret,
            "redirect_uri" to properties.redirectUri,
            "grant_type" to "authorization_code",
        )
        return post(form, action = "code 교환")
    }

    override fun refresh(refreshToken: String): GoogleTokens {
        val form = mapOf(
            "refresh_token" to refreshToken,
            "client_id" to properties.clientId,
            "client_secret" to properties.clientSecret,
            "grant_type" to "refresh_token",
        )
        return post(form, action = "토큰 갱신")
    }

    private fun post(form: Map<String, String>, action: String): GoogleTokens {
        val body = form.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(properties.tokenUri))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrElse {
            log.error("Google OAuth {} 전송 오류", action, it)
            throw GoogleOAuthException("Google OAuth $action 중 통신 오류", it)
        }

        if (response.statusCode() !in 200..299) {
            // 오류 응답에는 토큰이 없으므로 error 코드만 안전하게 추출해 로깅한다.
            val errorCode = runCatching {
                objectMapper.readTree(response.body()).path("error").asString()
            }.getOrDefault("")
            log.warn("Google OAuth {} 실패: HTTP {} error={}", action, response.statusCode(), errorCode)
            throw GoogleOAuthException("Google OAuth $action 실패: HTTP ${response.statusCode()}")
        }

        return runCatching { parse(response.body()) }.getOrElse {
            log.error("Google OAuth {} 응답 파싱 실패", action, it)
            throw GoogleOAuthException("Google OAuth $action 응답 파싱 실패", it)
        }
    }

    private fun parse(json: String): GoogleTokens {
        val root = objectMapper.readTree(json)
        val accessToken = root.path("access_token").asString()
        if (accessToken.isBlank()) {
            throw GoogleOAuthException("access_token 이 응답에 없습니다")
        }
        val refreshToken = root.path("refresh_token").asString().ifBlank { null }
        val expiresIn = root.path("expires_in").asLong(DEFAULT_EXPIRES_IN)
        return GoogleTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = Instant.now().plusSeconds(expiresIn),
        )
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val DEFAULT_EXPIRES_IN = 3600L
    }
}
