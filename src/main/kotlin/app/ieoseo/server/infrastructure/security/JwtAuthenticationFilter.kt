package app.ieoseo.server.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청당 1회, `Authorization: Bearer <access>` 를 검증해 SecurityContext 에 인증을 주입한다.
 *
 * 계약: `docs/06-백엔드/인증-도메인.md` §4 (JwtFilter 가 userId 주입).
 * - access 타입 JWT 만 인증으로 인정(refresh 오용 차단).
 * - 토큰이 없거나 유효하지 않으면 인증을 세팅하지 않고 다음 필터로 넘긴다
 *   (보호 엔드포인트는 SecurityConfig 의 authorize 규칙이 401 로 거른다).
 */
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveBearer(request)
        if (token != null && SecurityContextHolder.getContext().authentication == null) {
            authenticate(token, request)
        }
        filterChain.doFilter(request, response)
    }

    private fun authenticate(token: String, request: HttpServletRequest) {
        val claims = runCatching { jwtProvider.parse(token) }.getOrNull() ?: return
        if (claims.type != TokenType.ACCESS) return

        val principal = AuthPrincipal(userId = claims.userId, email = claims.email)
        val authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())
        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
        SecurityContextHolder.getContext().authentication = authentication
    }

    private fun resolveBearer(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null
        return header.substring(BEARER_PREFIX.length).trim().ifBlank { null }
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
