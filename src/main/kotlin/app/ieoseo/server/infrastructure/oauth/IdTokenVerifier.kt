package app.ieoseo.server.infrastructure.oauth

import app.ieoseo.server.domain.auth.AuthProvider
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.SecurityException
import java.security.Key

/**
 * Google/Apple 공통 ID token(JWT) 검증 로직.
 *
 * 1) 헤더 `kid` 로 [JwksKeyResolver] 에서 서명 공개키를 해석,
 * 2) jjwt 로 서명·`exp` 검증, `iss`(허용 발급자)·`aud`(client-id) 일치 확인,
 * 3) `sub`(providerId)·`email` 추출 → [OAuthIdentity].
 *
 * 모든 검증 실패(서명·만료·iss·aud·클레임 누락·설정 누락)는 [OAuthInvalidException] 으로 통일한다.
 */
abstract class IdTokenVerifier(
    private val keyResolver: JwksKeyResolver,
) : OAuthVerifier {

    /** 허용 발급자(`iss`) 집합. Google 은 두 형태를 모두 허용. */
    protected abstract val allowedIssuers: Set<String>

    /** ID token `aud` 와 일치해야 하는 client-id. */
    protected abstract val expectedAudience: String

    /** provider JWKS 엔드포인트. */
    protected abstract val jwksUri: String

    override fun verify(token: String): OAuthIdentity {
        if (expectedAudience.isBlank()) {
            throw OAuthInvalidException("$provider client-id 가 설정되지 않았습니다")
        }

        val claims = parseAndVerify(token)
        val subject = claims.subject ?: throw OAuthInvalidException("sub 클레임이 없습니다")
        val email = claims.get(CLAIM_EMAIL, String::class.java)
            ?: throw OAuthInvalidException("email 클레임이 없습니다")
        return OAuthIdentity(provider = provider, providerId = subject, email = email)
    }

    private fun parseAndVerify(token: String): Claims {
        try {
            return Jwts.parser()
                // 헤더 kid → JWKS 공개키 해석. 서명·exp 는 jjwt 가 검증한다.
                .keyLocator { header -> resolveKey(header[HEADER_KID]?.toString()) }
                .requireAudience(expectedAudience)
                .build()
                .parseSignedClaims(token)
                .payload
                .also { verifyIssuer(it) }
        } catch (ex: OAuthInvalidException) {
            throw ex
        } catch (ex: SecurityException) {
            throw OAuthInvalidException("ID token 서명이 유효하지 않습니다", ex)
        } catch (ex: JwtException) {
            throw OAuthInvalidException("ID token 이 유효하지 않습니다", ex)
        } catch (ex: IllegalArgumentException) {
            throw OAuthInvalidException("ID token 을 해석할 수 없습니다", ex)
        }
    }

    private fun resolveKey(kid: String?): Key {
        if (kid.isNullOrBlank()) throw OAuthInvalidException("ID token 에 kid 헤더가 없습니다")
        return keyResolver.resolve(jwksUri, kid)
    }

    private fun verifyIssuer(claims: Claims) {
        if (claims.issuer !in allowedIssuers) {
            throw OAuthInvalidException("허용되지 않은 발급자(iss)입니다")
        }
    }

    protected companion object {
        const val CLAIM_EMAIL = "email"
        const val HEADER_KID = "kid"
    }
}

/**
 * Google ID token 검증. JWKS=`https://www.googleapis.com/oauth2/v3/certs`,
 * iss=`accounts.google.com`(또는 `https://` 접두), aud=GOOGLE_CLIENT_ID. 계약: 이슈 #37.
 */
class GoogleOAuthVerifier(
    properties: OAuthProperties.Provider,
    keyResolver: JwksKeyResolver,
) : IdTokenVerifier(keyResolver) {
    override val provider = AuthProvider.GOOGLE
    override val allowedIssuers = setOf("https://accounts.google.com", "accounts.google.com")
    override val expectedAudience = properties.clientId
    override val jwksUri = "https://www.googleapis.com/oauth2/v3/certs"
}

/**
 * Apple ID token 검증. JWKS=`https://appleid.apple.com/auth/keys`,
 * iss=`https://appleid.apple.com`, aud=APPLE_CLIENT_ID. 계약: 이슈 #37.
 */
class AppleOAuthVerifier(
    properties: OAuthProperties.Provider,
    keyResolver: JwksKeyResolver,
) : IdTokenVerifier(keyResolver) {
    override val provider = AuthProvider.APPLE
    override val allowedIssuers = setOf("https://appleid.apple.com")
    override val expectedAudience = properties.clientId
    override val jwksUri = "https://appleid.apple.com/auth/keys"
}
