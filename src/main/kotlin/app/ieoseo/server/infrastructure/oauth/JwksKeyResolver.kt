package app.ieoseo.server.infrastructure.oauth

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * provider JWKS 엔드포인트에서 `kid` 에 해당하는 서명 공개키를 해석한다.
 *
 * HTTP/JWKS 실호출을 이 인터페이스 뒤로 추상화해, 테스트는 알려진 공개키를 반환하는
 * 가짜 구현을 주입한다(CI 외부 호출 금지). 계약: 이슈 #37.
 */
fun interface JwksKeyResolver {
    /** [jwksUri] 의 키 집합에서 [kid] 공개키를 반환한다. 실패 시 [OAuthInvalidException]. */
    fun resolve(jwksUri: String, kid: String): PublicKey
}

/**
 * 실제 JWKS 해석기 — JWKS JSON 을 가져와 RSA 공개키를 구성하고 단순 캐싱한다.
 *
 * 캐시는 (jwksUri, kid) 단위. 캐시 미스 시 1회 재조회(키 회전 대응), 그래도 없으면 실패.
 * 구글/애플 모두 RSA(`kty=RSA`, base64url `n`/`e`) 키를 제공한다.
 */
@Component
class HttpJwksKeyResolver(
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
        .build(),
) : JwksKeyResolver {

    private val cache = ConcurrentHashMap<String, PublicKey>()

    override fun resolve(jwksUri: String, kid: String): PublicKey {
        val cacheKey = "$jwksUri#$kid"
        cache[cacheKey]?.let { return it }

        val key = fetchKey(jwksUri, kid)
            ?: throw OAuthInvalidException("JWKS 에서 kid 를 찾을 수 없습니다")
        cache[cacheKey] = key
        return key
    }

    private fun fetchKey(jwksUri: String, kid: String): PublicKey? {
        val body = runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(jwksUri))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw OAuthInvalidException("JWKS 조회 실패: HTTP ${response.statusCode()}")
            }
            response.body()
        }.getOrElse {
            if (it is OAuthInvalidException) throw it
            throw OAuthInvalidException("JWKS 조회 중 오류", it)
        }

        val jwk = findKey(objectMapper.readTree(body).path("keys"), kid) ?: return null
        return toRsaPublicKey(jwk)
    }

    private fun findKey(keys: JsonNode, kid: String): JsonNode? {
        for (node in keys) {
            if (node.path("kid").asString() == kid) return node
        }
        return null
    }

    private fun toRsaPublicKey(jwk: JsonNode): PublicKey {
        val decoder = Base64.getUrlDecoder()
        val modulus = BigInteger(1, decoder.decode(jwk.path("n").asString()))
        val exponent = BigInteger(1, decoder.decode(jwk.path("e").asString()))
        return KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 5L
    }
}
