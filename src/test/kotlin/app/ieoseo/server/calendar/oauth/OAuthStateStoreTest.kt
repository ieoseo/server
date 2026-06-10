package app.ieoseo.server.calendar.oauth

import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** OAuthStateStore 단위 테스트(이슈 #9) — 일회용·CSRF state. */
class OAuthStateStoreTest {

    @Test
    fun `발급한 state 를 소비하면 사용자 id 를 돌려준다`() {
        val store = OAuthStateStore()
        val userId = UUID.randomUUID()

        val state = store.issue(userId)

        assertEquals(userId, store.consume(state))
    }

    @Test
    fun `state 는 일회용이라 두 번째 소비는 null`() {
        val store = OAuthStateStore()
        val userId = UUID.randomUUID()
        val state = store.issue(userId)

        store.consume(state)

        assertNull(store.consume(state))
    }

    @Test
    fun `모르는 state 는 null`() {
        val store = OAuthStateStore()
        assertNull(store.consume("unknown-state"))
    }

    @Test
    fun `만료된 state 는 null`() {
        val store = OAuthStateStore(ttl = Duration.ofMillis(1))
        val state = store.issue(UUID.randomUUID())
        Thread.sleep(5)

        assertNull(store.consume(state))
    }
}
