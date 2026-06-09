package app.ieoseo.server.global.security

import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * SupabaseJwtAuthenticationConverter 단위 테스트(ADR-0017).
 *
 * sub→userId, email(공백/부재→null), user_metadata 의 name/nickname/full_name/preferred_username
 * 순으로 표시 이름을 추출하는 순수 변환을 검증한다.
 */
class SupabaseJwtAuthenticationConverterTest {

    private val converter = SupabaseJwtAuthenticationConverter()
    private val sub = UUID.randomUUID()

    private fun jwt(email: String? = null, metadata: Map<String, Any>? = null): Jwt {
        val builder = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(sub.toString())
            .issuedAt(Instant.EPOCH)
            .expiresAt(Instant.EPOCH.plusSeconds(3600))
        email?.let { builder.claim("email", it) }
        metadata?.let { builder.claim("user_metadata", it) }
        return builder.build()
    }

    private fun principal(jwt: Jwt): AuthPrincipal =
        converter.convert(jwt)!!.principal as AuthPrincipal

    @Test
    fun `sub 를 userId 로 email claim 을 email 로 변환한다`() {
        val result = principal(jwt(email = "jiwoo@ieoseo.app"))

        assertEquals(sub, result.userId)
        assertEquals("jiwoo@ieoseo.app", result.email)
    }

    @Test
    fun `email claim 이 없으면 email 은 null`() {
        val result = principal(jwt(email = null))

        assertNull(result.email)
    }

    @Test
    fun `email claim 이 공백이면 email 은 null`() {
        val result = principal(jwt(email = "   "))

        assertNull(result.email)
    }

    @Test
    fun `user_metadata 의 name 을 표시 이름으로 쓴다`() {
        val result = principal(jwt(metadata = mapOf("name" to "지우")))

        assertEquals("지우", result.name)
    }

    @Test
    fun `name 이 없으면 nickname full_name preferred_username 순으로 본다`() {
        val result = principal(
            jwt(metadata = mapOf("full_name" to "박지우", "preferred_username" to "jiwoo")),
        )

        assertEquals("박지우", result.name)
    }

    @Test
    fun `이메일 없고 user_metadata 에 nickname 만 있으면 nickname 을 쓴다`() {
        val result = principal(jwt(email = null, metadata = mapOf("nickname" to "카카오지우")))

        assertNull(result.email)
        assertEquals("카카오지우", result.name)
    }

    @Test
    fun `user_metadata 가 없으면 name 은 null`() {
        val result = principal(jwt(metadata = null))

        assertNull(result.name)
    }

    @Test
    fun `user_metadata 후보 값이 모두 공백이면 name 은 null`() {
        val result = principal(jwt(metadata = mapOf("name" to "  ", "nickname" to "")))

        assertNull(result.name)
    }
}
