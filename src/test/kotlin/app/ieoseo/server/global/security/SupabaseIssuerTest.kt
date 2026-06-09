package app.ieoseo.server.global.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * supabaseIssuerFrom 단위 테스트(보안 하드닝 #1).
 *
 * JWKS URI 에서 Supabase 발급자(iss)를 도출한다. JWT 검증 시 iss 를 핀 고정해
 * 서명만 맞으면 통과하던 방어 공백을 메운다. 형식이 예상과 다르면 null(검증 생략, fail-safe).
 */
class SupabaseIssuerTest {

    @Test
    fun `JWKS URI 에서 auth v1 발급자를 도출한다`() {
        val jwks = "https://abcd1234.supabase.co/auth/v1/.well-known/jwks.json"
        assertEquals("https://abcd1234.supabase.co/auth/v1", supabaseIssuerFrom(jwks))
    }

    @Test
    fun `빈 문자열이면 null 을 반환한다`() {
        assertNull(supabaseIssuerFrom(""))
        assertNull(supabaseIssuerFrom("   "))
    }

    @Test
    fun `예상 접미사로 끝나지 않으면 null 을 반환한다`() {
        assertNull(supabaseIssuerFrom("https://abcd1234.supabase.co/auth/v1"))
        assertNull(supabaseIssuerFrom("not-a-url"))
    }
}
