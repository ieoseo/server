package app.ieoseo.server.infrastructure.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * in-memory refresh 저장소. 단위 테스트와 Redis 미연결 환경(슬라이스/로컬)에서 동작한다.
 *
 * 운영용 [RedisRefreshTokenStore] 빈이 있으면 그쪽이 우선이고, 없을 때만 폴백으로 등록된다.
 * TTL 은 만료 시각으로 보관하고 조회 시 만료분을 정리한다(프로세스 메모리 한정).
 */
@Component
@ConditionalOnMissingBean(RedisRefreshTokenStore::class)
@Primary
class InMemoryRefreshTokenStore(
    private val clock: () -> Instant = Instant::now,
) : RefreshTokenStore {

    private val store = ConcurrentHashMap<String, Instant>()

    private fun key(userId: UUID, jti: String) = "refresh:$userId:$jti"

    override fun store(userId: UUID, jti: String, ttl: Duration) {
        store[key(userId, jti)] = clock().plus(ttl)
    }

    override fun exists(userId: UUID, jti: String): Boolean {
        val expiresAt = store[key(userId, jti)] ?: return false
        if (!expiresAt.isAfter(clock())) {
            store.remove(key(userId, jti))
            return false
        }
        return true
    }

    override fun revoke(userId: UUID, jti: String) {
        store.remove(key(userId, jti))
    }

    override fun revokeAll(userId: UUID) {
        val prefix = "refresh:$userId:"
        store.keys.removeIf { it.startsWith(prefix) }
    }
}
