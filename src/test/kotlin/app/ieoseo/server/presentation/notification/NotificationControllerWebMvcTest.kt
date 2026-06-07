package app.ieoseo.server.presentation.notification

import app.ieoseo.server.infrastructure.security.AuthPrincipal
import app.ieoseo.server.infrastructure.security.JwtProvider
import app.ieoseo.server.infrastructure.security.SecurityConfig
import app.ieoseo.server.domain.notification.Notification
import app.ieoseo.server.domain.notification.NotificationType
import app.ieoseo.server.application.notification.NotificationList
import app.ieoseo.server.application.notification.NotificationService
import app.ieoseo.server.presentation.common.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * NotificationController 슬라이스 테스트 (@WebMvcTest + 실제 SecurityConfig, #46).
 *
 * notifications 는 인증 필수 — 미인증 401, 인증 주체 스코프, unreadCount/읽음 처리 매핑(서비스 mock).
 */
@WebMvcTest(NotificationController::class)
@Import(SecurityConfig::class, JwtProvider::class, GlobalExceptionHandler::class)
class NotificationControllerWebMvcTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var notificationService: NotificationService

    private val userId = UUID.randomUUID()

    private fun asUser(): Authentication =
        UsernamePasswordAuthenticationToken(AuthPrincipal(userId, "jiwoo@ieoseo.app"), null, emptyList())

    private fun <T> eqv(value: T): T {
        eq(value)
        return value
    }

    /** Kotlin non-null 파라미터용 Mockito any() 헬퍼(매처가 null 을 반환해 NPE 나는 문제 회피). */
    private fun anyPageable(): Pageable {
        any(Pageable::class.java)
        return Pageable.unpaged()
    }

    private fun sample(): Notification = Notification(
        userId = userId,
        type = NotificationType.DDAY,
        title = "토익 시험",
        body = "토익 시험이 3일 남았어요",
        refId = UUID.randomUUID(),
    )

    @Test
    fun `목록은 알림 배열과 unreadCount 를 함께 반환한다`() {
        `when`(notificationService.list(eqv(userId), anyPageable()))
            .thenReturn(NotificationList(items = listOf(sample()), unreadCount = 1, total = 1L))

        mockMvc.perform(get("/api/v1/notifications").with(authentication(asUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.unreadCount").value(1))
            .andExpect(jsonPath("$.data.items[0].type").value("DDAY"))
            .andExpect(jsonPath("$.data.items[0].read").value(false))
    }

    @Test
    fun `단건 읽음 처리는 200 과 read=true 를 반환한다`() {
        val id = UUID.randomUUID()
        val read = sample().apply { this.read = true }
        `when`(notificationService.markRead(eqv(userId), eqv(id))).thenReturn(read)

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", id).with(authentication(asUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.read").value(true))
    }

    @Test
    fun `read-all 은 갱신 건수를 반환한다`() {
        `when`(notificationService.markAllRead(eqv(userId))).thenReturn(3)

        mockMvc.perform(post("/api/v1/notifications/read-all").with(authentication(asUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.updated").value(3))
    }

    @Test
    fun `토큰 없이 접근하면 401 UNAUTHORIZED`() {
        mockMvc.perform(get("/api/v1/notifications"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }
}
