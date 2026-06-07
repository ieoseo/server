package app.ieoseo.server.infrastructure.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * 운영용 refresh 저장소(Redis). 계약: `docs/06-백엔드/인증-도메인.md` §3.
 *
 * 키 `refresh:{userId}:{jti}` 에 TTL 을 걸어 저장하고, 회전 시 이전 jti 를 삭제한다.
 * 재사용 감지·탈퇴 시 `refresh:{userId}:*` 패턴으로 전체 폐기한다.
 *
 * Redis 가 구성된 환경에서만 활성화한다(`ieoseo.jwt.refresh-store=redis`).
 * 미설정(테스트·로컬 슬라이스)에서는 [InMemoryRefreshTokenStore] 가 폴백으로 동작한다.
 */
@Component
@ConditionalOnProperty(name = ["ieoseo.jwt.refresh-store"], havingValue = "redis")
class RedisRefreshTokenStore(
    private val redis: StringRedisTemplate,
) : RefreshTokenStore {

    private fun key(userId: UUID, jti: String) = "refresh:$userId:$jti"

    override fun store(userId: UUID, jti: String, ttl: Duration) {
        redis.opsForValue().set(key(userId, jti), "1", ttl)
    }

    override fun exists(userId: UUID, jti: String): Boolean =
        redis.hasKey(key(userId, jti))

    override fun revoke(userId: UUID, jti: String) {
        redis.delete(key(userId, jti))
    }

    override fun revokeAll(userId: UUID) {
        val keys = redis.keys("refresh:$userId:*")
        if (keys.isNotEmpty()) {
            redis.delete(keys)
        }
    }
}
