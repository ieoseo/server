package app.ieoseo.server.infrastructure.oauth

import app.ieoseo.server.domain.auth.AuthProvider
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Provider 별 OAuthVerifier 단위 테스트 — JWKS/HTTP 실호출 없이 추상화 뒤 가짜를 주입한다.
 *
 * Google/Apple: RSA 키쌍을 테스트에서 만들어 ID token 을 서명하고, 공개키를 반환하는
 * 가짜 [JwksKeyResolver] 를 주입해 서명·iss·aud·exp 검증을 확인한다.
 * Kakao: 가짜 [KakaoUserClient] 로 사용자 정보 매핑을 확인한다.
 * 계약: 이슈 #37, `docs/05-API/auth.md`.
 */
class OAuthVerifierTest {

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
    private val kid = "test-kid-1"

    private fun resolver(key: PublicKey = keyPair.public): JwksKeyResolver =
        JwksKeyResolver { _, _ -> key }

    private fun idToken(
        issuer: String,
        audience: String,
        subject: String,
        email: String?,
        expiresAt: Instant = Instant.now().plusSeconds(600),
        signingKey: java.security.PrivateKey = keyPair.private,
    ): String {
        val builder = Jwts.builder()
            .header().keyId(kid).and()
            .issuer(issuer)
            .audience().add(audience).and()
            .subject(subject)
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(expiresAt))
        if (email != null) builder.claim("email", email)
        return builder.signWith(signingKey).compact()
    }

    // ---- Google ----

    private fun googleVerifier(): GoogleOAuthVerifier =
        GoogleOAuthVerifier(
            properties = OAuthProperties.Provider(clientId = "google-client-id"),
            keyResolver = resolver(),
        )

    @Test
    fun `Google 검증은 유효한 ID token 에서 sub 와 email 을 추출한다`() {
        val token = idToken(
            issuer = "https://accounts.google.com",
            audience = "google-client-id",
            subject = "google-sub",
            email = "user@gmail.com",
        )

        val identity = googleVerifier().verify(token)

        assertEquals(AuthProvider.GOOGLE, identity.provider)
        assertEquals("google-sub", identity.providerId)
        assertEquals("user@gmail.com", identity.email)
    }

    @Test
    fun `Google 검증은 iss 가 다르면 OAuthInvalidException`() {
        val token = idToken("https://evil.example.com", "google-client-id", "s", "e@x")
        assertFailsWith<OAuthInvalidException> { googleVerifier().verify(token) }
    }

    @Test
    fun `Google 검증은 aud 가 다르면 OAuthInvalidException`() {
        val token = idToken("https://accounts.google.com", "other-client-id", "s", "e@x")
        assertFailsWith<OAuthInvalidException> { googleVerifier().verify(token) }
    }

    @Test
    fun `Google 검증은 만료된 토큰이면 OAuthInvalidException`() {
        val token = idToken(
            issuer = "https://accounts.google.com",
            audience = "google-client-id",
            subject = "s",
            email = "e@x",
            expiresAt = Instant.now().minusSeconds(60),
        )
        assertFailsWith<OAuthInvalidException> { googleVerifier().verify(token) }
    }

    @Test
    fun `Google 검증은 서명이 다른 키면 OAuthInvalidException`() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val token = idToken("https://accounts.google.com", "google-client-id", "s", "e@x", signingKey = other.private)
        assertFailsWith<OAuthInvalidException> { googleVerifier().verify(token) }
    }

    // ---- Apple ----

    @Test
    fun `Apple 검증은 유효한 ID token 에서 sub 와 email 을 추출한다`() {
        val verifier = AppleOAuthVerifier(
            properties = OAuthProperties.Provider(clientId = "apple-client-id"),
            keyResolver = resolver(),
        )
        val token = idToken(
            issuer = "https://appleid.apple.com",
            audience = "apple-client-id",
            subject = "apple-sub",
            email = "user@privaterelay.appleid.com",
        )

        val identity = verifier.verify(token)

        assertEquals(AuthProvider.APPLE, identity.provider)
        assertEquals("apple-sub", identity.providerId)
        assertEquals("user@privaterelay.appleid.com", identity.email)
    }

    // ---- Kakao ----

    @Test
    fun `Kakao 검증은 사용자 정보 API 응답을 매핑한다`() {
        val client = KakaoUserClient { token ->
            assertEquals("kakao-access-token", token)
            KakaoUser(id = 123456789L, email = "user@kakao.com")
        }
        val verifier = KakaoOAuthVerifier(client)

        val identity = verifier.verify("kakao-access-token")

        assertEquals(AuthProvider.KAKAO, identity.provider)
        assertEquals("123456789", identity.providerId)
        assertEquals("user@kakao.com", identity.email)
    }

    @Test
    fun `Kakao 검증은 client 가 실패하면 OAuthInvalidException`() {
        val client = KakaoUserClient { throw OAuthInvalidException("kakao api 401") }
        val verifier = KakaoOAuthVerifier(client)

        assertFailsWith<OAuthInvalidException> { verifier.verify("bad") }
    }

    @Test
    fun `Registry 는 provider 로 verifier 를 찾고 없으면 OAuthInvalidException`() {
        val registry = OAuthVerifierRegistry(listOf(googleVerifier()))

        assertEquals(AuthProvider.GOOGLE, registry.forProvider(AuthProvider.GOOGLE).provider)
        assertFailsWith<OAuthInvalidException> { registry.forProvider(AuthProvider.APPLE) }
    }
}
