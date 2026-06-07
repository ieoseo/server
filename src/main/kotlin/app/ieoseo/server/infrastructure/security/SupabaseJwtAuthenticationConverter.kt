package app.ieoseo.server.infrastructure.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

/** Supabase JWT(sub=UUID, email) → AuthPrincipal 인증 토큰. 순수 변환(저장소 의존 없음). */
class SupabaseJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val userId = UUID.fromString(jwt.subject)
        val email = jwt.getClaimAsString("email").orEmpty()
        val principal = AuthPrincipal(userId = userId, email = email)
        return UsernamePasswordAuthenticationToken(principal, jwt, emptyList())
    }
}
