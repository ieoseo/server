package app.ieoseo.server.presentation.event

import app.ieoseo.server.infrastructure.security.AuthPrincipal
import app.ieoseo.server.infrastructure.security.JwtProvider
import app.ieoseo.server.infrastructure.security.SecurityConfig
import app.ieoseo.server.common.NotFoundException
import app.ieoseo.server.domain.event.DDayResult
import app.ieoseo.server.domain.event.Event
import app.ieoseo.server.domain.event.EventPhase
import app.ieoseo.server.domain.event.EventType
import app.ieoseo.server.domain.event.Urgency
import app.ieoseo.server.application.event.EventService
import app.ieoseo.server.presentation.common.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

/**
 * EventController 슬라이스 테스트 (@WebMvcTest + 실제 SecurityConfig).
 *
 * events 는 인증 필수(#30) — 보안 체인을 실제 로드해 미인증 401 과 인증 주체 스코프를 검증한다.
 * 인증 주체는 [authentication] postprocessor 로 [AuthPrincipal] 을 주입한다(서비스는 mock).
 */
@WebMvcTest(EventController::class)
@Import(SecurityConfig::class, JwtProvider::class, GlobalExceptionHandler::class)
class EventControllerWebMvcTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var eventService: EventService

    private val userId = UUID.randomUUID()

    private fun asUser(): Authentication =
        UsernamePasswordAuthenticationToken(AuthPrincipal(userId, "jiwoo@ieoseo.app"), null, emptyList())

    /** Kotlin non-null 파라미터용 eq() 헬퍼(매처가 null 을 반환해 NPE 나는 문제 회피). */
    private fun <T> eqv(value: T): T {
        eq(value)
        return value
    }

    @Test
    fun `단건 조회는 envelope 와 D-Day 필드를 포함한다`() {
        val id = UUID.randomUUID()
        val event = Event(
            id = id,
            userId = userId,
            type = EventType.T1_DDAY,
            title = "정처기 실기",
            date = LocalDate.of(2026, 8, 2),
        )
        `when`(eventService.findById(eqv(userId), eqv(id))).thenReturn(event)
        `when`(eventService.dDay(anyEvent(), anyDate())).thenReturn(
            DDayResult(daysRemaining = 59, label = "D-59", phase = EventPhase.UPCOMING, urgency = Urgency.LOW),
        )

        mockMvc.perform(get("/api/v1/events/{id}", id).with(authentication(asUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("정처기 실기"))
            .andExpect(jsonPath("$.data.type").value("T1_DDAY"))
            .andExpect(jsonPath("$.data.dday.label").value("D-59"))
            .andExpect(jsonPath("$.error").doesNotExist())
    }

    @Test
    fun `토큰 없이 접근하면 401 UNAUTHORIZED`() {
        mockMvc.perform(get("/api/v1/events/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `없는(또는 타인) 이벤트 조회는 404 NOT_FOUND envelope`() {
        val id = UUID.randomUUID()
        `when`(eventService.findById(eqv(userId), eqv(id))).thenThrow(NotFoundException("Event", id))

        mockMvc.perform(get("/api/v1/events/{id}", id).with(authentication(asUser())))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
    }

    @Test
    fun `제목이 비면 400 VALIDATION_ERROR 와 필드 details`() {
        val body = """
            { "type": "T1_DDAY", "title": "", "date": "2026-08-02" }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/events")
                .with(authentication(asUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[0].field").value("title"))
    }

    /** Kotlin non-null 파라미터용 Mockito any() 헬퍼(매처가 null 을 반환해 NPE 나는 문제 회피). */
    private fun anyEvent(): Event {
        org.mockito.ArgumentMatchers.any(Event::class.java)
        return Event(userId = userId, type = EventType.T1_DDAY, title = "x", date = LocalDate.now())
    }

    private fun anyDate(): LocalDate {
        org.mockito.ArgumentMatchers.any(LocalDate::class.java)
        return LocalDate.now()
    }
}
