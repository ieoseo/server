package app.ieoseo.server.infrastructure.oauth

import app.ieoseo.server.domain.auth.AuthProvider
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Kakao 사용자 정보 API 응답 중 본 구현이 사용하는 필드.
 *
 * - [id]: Kakao 회원번호(providerId 로 사용).
 * - [email]: 카카오계정 이메일(동의 시 제공).
 */
data class KakaoUser(
    val id: Long,
    val email: String,
)

/**
 * Kakao access token 으로 사용자 정보를 조회하는 클라이언트.
 *
 * HTTP 실호출을 이 인터페이스 뒤로 추상화한다(테스트는 가짜 주입). 조회 실패 시 [OAuthInvalidException].
 */
fun interface KakaoUserClient {
    fun fetchUser(accessToken: String): KakaoUser
}

/**
 * 실제 Kakao 사용자 조회 — `https://kapi.kakao.com/v2/user/me` 를 Bearer 로 호출한다. 계약: 이슈 #37.
 *
 * 응답 JSON 의 `id`(회원번호)와 `kakao_account.email` 을 추출한다. 비2xx·이메일 미동의는 실패로 본다.
 */
@Component
class HttpKakaoUserClient(
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .build(),
) : KakaoUserClient {

    override fun fetchUser(accessToken: String): KakaoUser {
        val body = runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(USER_ME_URI))
                .header("Authorization", "Bearer $accessToken")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw OAuthInvalidException("Kakao 사용자 조회 실패: HTTP ${response.statusCode()}")
            }
            response.body()
        }.getOrElse {
            if (it is OAuthInvalidException) throw it
            throw OAuthInvalidException("Kakao 사용자 조회 중 오류", it)
        }

        val root = objectMapper.readTree(body)
        val idNode = root.path("id")
        if (!idNode.isNumber) throw OAuthInvalidException("Kakao 응답에 id 가 없습니다")
        val email = root.path("kakao_account").path("email").asString()
        if (email.isBlank()) throw OAuthInvalidException("Kakao 이메일 제공에 동의하지 않았습니다")
        return KakaoUser(id = idNode.asLong(), email = email)
    }

    private companion object {
        const val USER_ME_URI = "https://kapi.kakao.com/v2/user/me"
        const val REQUEST_TIMEOUT_SECONDS = 5L
    }
}

/**
 * Kakao OAuth 검증. access token → 사용자 정보 API → (providerId=id, email). 계약: 이슈 #37.
 */
@Component
class KakaoOAuthVerifier(
    private val client: KakaoUserClient,
) : OAuthVerifier {
    override val provider = AuthProvider.KAKAO

    override fun verify(token: String): OAuthIdentity {
        val user = client.fetchUser(token)
        return OAuthIdentity(
            provider = AuthProvider.KAKAO,
            providerId = user.id.toString(),
            email = user.email,
        )
    }
}
