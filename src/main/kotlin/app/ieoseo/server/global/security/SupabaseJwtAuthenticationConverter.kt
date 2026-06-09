package app.ieoseo.server.global.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

/**
 * Supabase JWT(sub=UUID, email, user_metadata) → AuthPrincipal 인증 토큰. 순수 변환(저장소 의존 없음).
 *
 * email 은 미제공 provider(Kakao 등)에서 비어 올 수 있어 nullable 로 추출한다(ADR-0017).
 * 표시 이름은 user_metadata(Map)의 [NAME_CLAIM_KEYS] 순으로 첫 non-blank 값을 사용한다.
 */
class SupabaseJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val userId = UUID.fromString(jwt.subject)
        val email = jwt.getClaimAsString("email")?.trim()?.ifBlank { null }
        val name = displayName(jwt)
        val principal = AuthPrincipal(userId = userId, email = email, name = name)
        return UsernamePasswordAuthenticationToken(principal, jwt, emptyList())
    }

    /** user_metadata 의 후보 키를 순서대로 보며 첫 non-blank 표시 이름을 반환. 없으면 null. */
    private fun displayName(jwt: Jwt): String? {
        val metadata = jwt.getClaimAsMap("user_metadata") ?: return null
        return NAME_CLAIM_KEYS
            .asSequence()
            .mapNotNull { (metadata[it] as? String)?.trim()?.ifBlank { null } }
            .firstOrNull()
    }

    private companion object {
        val NAME_CLAIM_KEYS = listOf("name", "nickname", "full_name", "preferred_username")
    }
}
