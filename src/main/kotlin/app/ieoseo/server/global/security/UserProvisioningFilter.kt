package app.ieoseo.server.global.security

import app.ieoseo.server.user.domain.User
import app.ieoseo.server.user.repository.UserRepository
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
            // 이메일은 nullable(미제공 provider 지원, ADR-0017) — placeholder 합성하지 않는다.
            repo.save(User(id = principal.userId, email = principal.email, nickname = defaultNickname(principal)))
        }
        filterChain.doFilter(request, response)
    }

    /** 닉네임 우선순위: provider 표시 이름 → 이메일 local-part → "user". 모두 20자 이내. */
    private fun defaultNickname(principal: AuthPrincipal): String =
        principal.name?.trim()?.take(NICKNAME_MAX_LENGTH)?.ifBlank { null }
            ?: principal.email?.substringBefore('@')?.take(NICKNAME_MAX_LENGTH)?.ifBlank { null }
            ?: "user"

    private companion object { const val NICKNAME_MAX_LENGTH = 20 }
}
