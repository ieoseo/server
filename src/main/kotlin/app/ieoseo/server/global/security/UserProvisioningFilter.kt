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
        if (principal != null && repo != null) {
            val user = repo.findById(principal.userId).orElse(null)
            if (user == null) {
                // 이메일은 nullable(미제공 provider 지원, ADR-0017) — placeholder 합성하지 않는다.
                repo.save(User(id = principal.userId, email = principal.email, nickname = defaultNickname(principal)))
                // 이번 요청에 막 생성된 신규 사용자임을 표시 → AuthController.me 가 isNew 로 반환
                // (client 가 닉네임 설정 화면을 띄우는 신호). provisioning 은 보통 첫 /auth/me 요청.
                request.setAttribute(NEW_USER_REQUEST_ATTR, true)
            } else if (!user.isActive) {
                // 탈퇴 사용자는 토큰 만료 전까지 유효한 Supabase JWT 로 접근할 수 있으므로 명시 차단한다.
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "비활성화된 계정입니다")
                return
            }
        }
        filterChain.doFilter(request, response)
    }

    /** 닉네임 우선순위: provider 표시 이름 → 이메일 local-part → "user". 모두 20자 이내. */
    private fun defaultNickname(principal: AuthPrincipal): String =
        principal.name?.trim()?.take(NICKNAME_MAX_LENGTH)?.ifBlank { null }
            ?: principal.email?.substringBefore('@')?.take(NICKNAME_MAX_LENGTH)?.ifBlank { null }
            ?: "user"

    companion object {
        /** 신규 provisioning 표시용 요청 attribute 키(AuthController.me 가 읽어 isNew 반환). */
        const val NEW_USER_REQUEST_ATTR = "app.ieoseo.newUser"
        private const val NICKNAME_MAX_LENGTH = 20
    }
}
