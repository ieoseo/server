package app.ieoseo.server.calendar.oauth

import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.calendar.repository.CalendarConnectionRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** CalendarOAuthService 단위 테스트(이슈 #9). */
class CalendarOAuthServiceTest {

    private val repo: CalendarConnectionRepository = mock(CalendarConnectionRepository::class.java)
    private val stateStore = OAuthStateStore()

    private val configured = GoogleOAuthProperties(
        clientId = "cid",
        clientSecret = "sec",
        redirectUri = "https://api.example.com/api/v1/calendar/oauth/google/callback",
        returnDeeplink = "app.ieoseo://calendar-callback",
    )

    private class FakeOAuthClient(
        var tokens: GoogleTokens? = null,
        var fail: Boolean = false,
    ) : GoogleOAuthClient {
        override fun exchangeCode(code: String): GoogleTokens {
            if (fail) throw GoogleOAuthException("교환 실패")
            return tokens!!
        }

        override fun refresh(refreshToken: String): GoogleTokens = tokens!!
    }

    private fun service(
        props: GoogleOAuthProperties = configured,
        client: GoogleOAuthClient = FakeOAuthClient(),
    ) = CalendarOAuthService(props, stateStore, client, repo)

    @Test
    fun `설정되어 있으면 동의 URL 에 authUri 와 state 가 포함된다`() {
        val url = service().buildGoogleAuthUrl(UUID.randomUUID())

        assertTrue(url.startsWith(configured.authUri))
        assertTrue(url.contains("client_id=cid"))
        assertTrue(url.contains("access_type=offline"))
        assertTrue(url.contains("state="))
    }

    @Test
    fun `설정이 없으면 예외를 던진다`() {
        val unconfigured = service(props = GoogleOAuthProperties())
        assertThrows<GoogleOAuthException> { unconfigured.buildGoogleAuthUrl(UUID.randomUUID()) }
    }

    @Test
    fun `콜백 성공 → 연결 저장 + success 딥링크`() {
        val userId = UUID.randomUUID()
        val state = stateStore.issue(userId)
        val client = FakeOAuthClient(
            tokens = GoogleTokens("acc", "ref", Instant.now().plusSeconds(3600)),
        )
        `when`(repo.findByUserIdAndProvider(userId, CalendarProvider.GOOGLE))
            .thenReturn(Optional.empty())

        val deeplink = service(client = client).handleGoogleCallback("auth-code", state, null)

        assertTrue(deeplink.startsWith("app.ieoseo://calendar-callback?status=success"))
        val captor = ArgumentCaptor.forClass(CalendarConnection::class.java)
        verify(repo).save(captor.capture())
        assertEquals(userId, captor.value.userId)
        assertEquals("acc", captor.value.accessToken)
        assertEquals("ref", captor.value.refreshToken)
    }

    @Test
    fun `무효 state 콜백 → error 딥링크 + 저장 안 함`() {
        val deeplink = service().handleGoogleCallback("auth-code", "bad-state", null)

        assertTrue(deeplink.contains("status=error"))
        assertTrue(deeplink.contains("reason=invalid_state"))
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any(CalendarConnection::class.java))
    }

    @Test
    fun `동의 거부(error 파라미터) → error 딥링크`() {
        val deeplink = service().handleGoogleCallback(null, null, "access_denied")

        assertTrue(deeplink.contains("status=error"))
        assertTrue(deeplink.contains("reason=consent_denied"))
    }

    @Test
    fun `토큰 교환 실패 → error 딥링크 + 저장 안 함`() {
        val userId = UUID.randomUUID()
        val state = stateStore.issue(userId)

        val deeplink = service(client = FakeOAuthClient(fail = true))
            .handleGoogleCallback("auth-code", state, null)

        assertTrue(deeplink.contains("status=error"))
        assertTrue(deeplink.contains("reason=token_exchange_failed"))
        verify(repo, never()).save(org.mockito.ArgumentMatchers.any(CalendarConnection::class.java))
    }
}
