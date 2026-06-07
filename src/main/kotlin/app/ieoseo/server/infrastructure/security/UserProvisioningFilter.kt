package app.ieoseo.server.infrastructure.security

import app.ieoseo.server.domain.auth.User
import app.ieoseo.server.infrastructure.persistence.auth.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 인증된 주체(AuthPrincipal)에 대응하는 User 행을 보장(없으면 생성). FK(user_id→users.id) 충족용.
 * UserRepository 는 ObjectProvider 로 지연 주입 — 슬라이스 테스트(저장소 없음)에서는 no-op.
 */
class UserProvisioningFilter(
    private val userRepositoryProvider: ObjectProvider<UserRepository>,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? AuthPrincipal
        val repo = userRepositoryProvider.getIfAvailable()
        if (principal != null && repo != null && !repo.existsById(principal.userId)) {
            val email = principal.email.ifBlank { "${principal.userId}@users.ieoseo.local" }
            repo.save(User(id = principal.userId, email = email, nickname = defaultNickname(principal.email)))
        }
        filterChain.doFilter(request, response)
    }

    private fun defaultNickname(email: String): String =
        email.substringBefore('@').take(20).ifBlank { "user" }
}
