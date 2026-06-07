package app.ieoseo.server.infrastructure.auth

import java.time.Duration
import java.util.UUID

/**
 * refresh 토큰(jti) 저장소 추상화. 계약: `docs/06-백엔드/인증-도메인.md` §3.
 *
 * 키 규약: `refresh:{userId}:{jti}`. 운영은 Redis([RedisRefreshTokenStore]),
 * 테스트/Redis 미연결 환경은 in-memory([InMemoryRefreshTokenStore]).
 *
 * 회전(rotation): 새 jti [store] + 이전 jti [revoke].
 * 재사용 감지 시 [revokeAll] 로 사용자 전체 폐기.
 */
interface RefreshTokenStore {

    /** refresh jti 를 [ttl] 만큼 저장한다. */
    fun store(userId: UUID, jti: String, ttl: Duration)

    /** 해당 jti 가 살아 있는지(폐기되지 않았는지) 확인한다. */
    fun exists(userId: UUID, jti: String): Boolean

    /** 단일 jti 를 폐기한다(로그아웃·회전 시 이전 토큰). */
    fun revoke(userId: UUID, jti: String)

    /** 사용자의 모든 refresh 를 폐기한다(재사용 감지·탈퇴). */
    fun revokeAll(userId: UUID)
}
