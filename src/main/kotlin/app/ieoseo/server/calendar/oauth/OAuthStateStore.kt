package app.ieoseo.server.calendar.oauth

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * OAuth state(CSRF 방지) 저장소(이슈 #9). connect 시작 시 사용자별 일회용 state 를 발급하고,
 * 콜백에서 소비(consume)해 어떤 사용자의 연동인지 안전하게 식별한다.
 *
 * 인메모리·TTL(기본 10분)·단일 사용(consume 시 제거). 단일 인스턴스(Azure Container Apps, minReplicas=1,
 * ADR-0018) 전제이며, 재시작 시 진행 중 state 는 소실(사용자가 재시도). 다중 인스턴스로 확장하면 Redis 로 교체.
 */
@Component
class OAuthStateStore(
    private val ttl: Duration = DEFAULT_TTL,
) {
    private data class Entry(val userId: UUID, val expiresAt: Instant)

    private val states = ConcurrentHashMap<String, Entry>()
    private val random = SecureRandom()

    /** 사용자에 대한 일회용 state 를 발급한다. */
    fun issue(userId: UUID): String {
        purgeExpired()
        val state = newToken()
        states[state] = Entry(userId, Instant.now().plus(ttl))
        return state
    }

    /** state 를 소비해 사용자 id 를 돌려준다. 없거나 만료/재사용이면 null. */
    fun consume(state: String): UUID? {
        val entry = states.remove(state) ?: return null
        if (entry.expiresAt.isBefore(Instant.now())) return null
        return entry.userId
    }

    private fun purgeExpired() {
        val now = Instant.now()
        states.entries.removeIf { it.value.expiresAt.isBefore(now) }
    }

    private fun newToken(): String {
        val bytes = ByteArray(STATE_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        val DEFAULT_TTL: Duration = Duration.ofMinutes(10)
        const val STATE_BYTES = 32
    }
}
