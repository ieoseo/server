package app.ieoseo.server.global.security

import java.util.UUID

/**
 * 인증된 요청 주체. SupabaseJwtAuthenticationConverter 가 Supabase JWT(sub/email/user_metadata)에서
 * 추출해 SecurityContext 의 Authentication principal 로 주입한다.
 *
 * email 은 nullable(이메일 미제공 provider 인 Kakao 등, ADR-0017). name 은 provider 가 제공한
 * 표시 이름(user_metadata 의 name/nickname/full_name/preferred_username)으로 없으면 null.
 */
data class AuthPrincipal(
    val userId: UUID,
    val email: String?,
    val name: String? = null,
)
