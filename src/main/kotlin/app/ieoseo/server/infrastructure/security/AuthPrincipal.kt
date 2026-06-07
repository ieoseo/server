package app.ieoseo.server.infrastructure.security

import java.util.UUID

/**
 * 인증된 요청 주체. JwtAuthenticationFilter 가 access 토큰에서 추출해
 * SecurityContext 의 Authentication principal 로 주입한다.
 */
data class AuthPrincipal(
    val userId: UUID,
    val email: String,
)
