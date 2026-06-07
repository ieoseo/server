package app.ieoseo.server.application.auth

import app.ieoseo.server.domain.auth.User
import app.ieoseo.server.infrastructure.auth.RefreshTokenStore
import app.ieoseo.server.infrastructure.security.InvalidTokenException
import app.ieoseo.server.infrastructure.security.IssuedToken
import app.ieoseo.server.infrastructure.security.JwtProvider
import app.ieoseo.server.infrastructure.security.TokenType
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * 발급된 access + refresh 쌍과 소유 사용자 id.
 */
data class TokenPair(
    val userId: java.util.UUID,
    val accessToken: IssuedToken,
    val refreshToken: IssuedToken,
)

/**
 * 토큰 발급·회전·폐기. 계약: `docs/06-백엔드/인증-도메인.md` §3.
 *
 * - [issue]: access + refresh 발급, refresh jti 를 저장소에 등록.
 * - [rotate]: refresh 검증 → 저장소 확인 → 이전 jti 폐기 → 새 쌍 발급(회전).
 *   저장소에 없는(이미 폐기된) refresh 재사용은 **재사용 감지** 로 보고
 *   사용자 전체 refresh 를 폐기한 뒤 거부한다.
 * - [revoke]: 로그아웃 — 해당 refresh jti 만 폐기.
 */
@Service
class TokenService(
    private val jwtProvider: JwtProvider,
    private val refreshTokenStore: RefreshTokenStore,
) {
    fun issue(user: User): TokenPair {
        val access = jwtProvider.issueAccess(user.id, user.email)
        val refresh = jwtProvider.issueRefresh(user.id, user.email)
        refreshTokenStore.store(user.id, refresh.jti, Duration.ofSeconds(refresh.expiresInSeconds))
        return TokenPair(user.id, access, refresh)
    }

    fun rotate(refreshTokenValue: String): TokenPair {
        val claims = jwtProvider.parse(refreshTokenValue) // 만료·위조 → InvalidTokenException
        if (claims.type != TokenType.REFRESH) {
            throw InvalidTokenException("refresh 토큰이 아닙니다")
        }

        if (!refreshTokenStore.exists(claims.userId, claims.jti)) {
            // 유효 서명이지만 저장소에 없음 = 이미 회전/폐기된 토큰 재사용 → 전체 폐기
            refreshTokenStore.revokeAll(claims.userId)
            throw InvalidTokenException("이미 사용되었거나 폐기된 refresh 토큰입니다")
        }

        refreshTokenStore.revoke(claims.userId, claims.jti)

        val access = jwtProvider.issueAccess(claims.userId, claims.email)
        val refresh = jwtProvider.issueRefresh(claims.userId, claims.email)
        refreshTokenStore.store(claims.userId, refresh.jti, Duration.ofSeconds(refresh.expiresInSeconds))
        return TokenPair(claims.userId, access, refresh)
    }

    /** 로그아웃: 전달된 refresh jti 만 폐기한다. 유효하지 않은 토큰은 조용히 무시(이미 무효). */
    fun revoke(refreshTokenValue: String) {
        val claims = runCatching { jwtProvider.parse(refreshTokenValue) }.getOrNull() ?: return
        refreshTokenStore.revoke(claims.userId, claims.jti)
    }

    /**
     * 탈퇴: 해당 사용자의 모든 refresh 를 폐기한다(이후 재발급 불가).
     * access 는 짧은 TTL 로 자연 만료된다(인증-도메인 §3).
     */
    fun revokeAll(userId: java.util.UUID) {
        refreshTokenStore.revokeAll(userId)
    }
}
