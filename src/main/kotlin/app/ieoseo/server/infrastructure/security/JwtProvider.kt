package app.ieoseo.server.infrastructure.security

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * 발급된 토큰 + 만료 메타. [value] 는 직렬화된 JWT, [expiresAt]/[expiresInSeconds] 는 클라이언트 안내용.
 */
data class IssuedToken(
    val value: String,
    val type: TokenType,
    val jti: String,
    val expiresAt: Instant,
    val expiresInSeconds: Long,
)

/**
 * 검증된 토큰의 클레임. [type] 으로 access/refresh 오용을 차단한다.
 */
data class TokenClaims(
    val userId: UUID,
    val email: String,
    val jti: String,
    val type: TokenType,
    val expiresAt: Instant,
)

/**
 * HMAC(SHA-256) 서명 JWT 발급·검증. 계약: `docs/06-백엔드/인증-도메인.md` §3.
 *
 * claims: `sub`(userId), `email`, `jti`, `type`(ACCESS/REFRESH), `iat`, `exp`.
 * 서명 키([JwtProperties.secret])와 TTL 은 설정에서 주입한다(하드코딩 금지).
 * [clock] 은 테스트에서 만료를 결정적으로 검증하기 위한 시간 공급자.
 */
@Component
class JwtProvider(
    private val properties: JwtProperties,
    private val clock: () -> Instant = Instant::now,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray(Charsets.UTF_8))

    fun issueAccess(userId: UUID, email: String): IssuedToken =
        issue(userId, email, TokenType.ACCESS, properties.accessTtlSeconds)

    fun issueRefresh(userId: UUID, email: String): IssuedToken =
        issue(userId, email, TokenType.REFRESH, properties.refreshTtlSeconds)

    private fun issue(userId: UUID, email: String, type: TokenType, ttlSeconds: Long): IssuedToken {
        val now = clock()
        val expiresAt = now.plusSeconds(ttlSeconds)
        val jti = UUID.randomUUID().toString()

        val token = Jwts.builder()
            .subject(userId.toString())
            .claim(CLAIM_EMAIL, email)
            .claim(CLAIM_TYPE, type.name)
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()

        return IssuedToken(
            value = token,
            type = type,
            jti = jti,
            expiresAt = expiresAt,
            expiresInSeconds = ttlSeconds,
        )
    }

    /**
     * 토큰을 검증하고 클레임을 복원한다. 만료·위조·손상 시 [InvalidTokenException].
     */
    fun parse(token: String): TokenClaims {
        try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .clock { Date.from(clock()) }
                .build()
                .parseSignedClaims(token)
                .payload

            val userId = UUID.fromString(claims.subject)
            val email = claims.get(CLAIM_EMAIL, String::class.java)
                ?: throw InvalidTokenException("email 클레임이 없습니다")
            val type = claims.get(CLAIM_TYPE, String::class.java)
                ?.let { runCatching { TokenType.valueOf(it) }.getOrNull() }
                ?: throw InvalidTokenException("type 클레임이 올바르지 않습니다")
            val jti = claims.id ?: throw InvalidTokenException("jti 클레임이 없습니다")

            return TokenClaims(
                userId = userId,
                email = email,
                jti = jti,
                type = type,
                expiresAt = claims.expiration.toInstant(),
            )
        } catch (ex: ExpiredJwtException) {
            throw InvalidTokenException("토큰이 만료되었습니다", ex)
        } catch (ex: JwtException) {
            throw InvalidTokenException("토큰이 유효하지 않습니다", ex)
        } catch (ex: IllegalArgumentException) {
            throw InvalidTokenException("토큰을 해석할 수 없습니다", ex)
        }
    }

    private companion object {
        const val CLAIM_EMAIL = "email"
        const val CLAIM_TYPE = "type"
    }
}
