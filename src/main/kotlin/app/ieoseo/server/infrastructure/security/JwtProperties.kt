package app.ieoseo.server.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * JWT 서명·만료 설정. 값은 `application.yaml`/환경변수에서 주입한다(하드코딩 금지).
 *
 * 계약: `docs/06-백엔드/인증-도메인.md` §3.
 * - [secret]: HMAC 서명 키(`JWT_SECRET`, 운영은 32바이트+ 무작위).
 * - [accessTtlSeconds]: access 토큰 TTL(기본 1800s = 30분).
 * - [refreshTtlSeconds]: refresh 토큰 TTL(기본 1209600s = 14일).
 */
@ConfigurationProperties(prefix = "ieoseo.jwt")
data class JwtProperties(
    val secret: String,
    val accessTtlSeconds: Long = 1800,
    val refreshTtlSeconds: Long = 1_209_600,
)
