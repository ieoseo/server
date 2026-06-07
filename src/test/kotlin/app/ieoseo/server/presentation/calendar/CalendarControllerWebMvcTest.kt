package app.ieoseo.server.presentation.calendar

import app.ieoseo.server.application.calendar.CalendarService
import app.ieoseo.server.application.calendar.CalendarSyncService
import app.ieoseo.server.domain.calendar.CalendarConnection
import app.ieoseo.server.domain.calendar.CalendarProvider
import app.ieoseo.server.domain.calendar.ConnectionStatus
import app.ieoseo.server.domain.calendar.ExternalEvent
import app.ieoseo.server.infrastructure.security.AuthPrincipal
import app.ieoseo.server.infrastructure.security.SecurityConfig
import app.ieoseo.server.presentation.common.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

/**
 * CalendarController 슬라이스 테스트 (@WebMvcTest + 실제 SecurityConfig, 이슈 #59).
 *
 * 캘린더 엔드포인트는 인증 필수 — 미인증 401 과 인증 주체 스코프, envelope, 토큰 비노출을 검증한다.
 */
@WebMvcTest(CalendarController::class)
@Import(SecurityConfig::class, GlobalExceptionHandler::class)
class CalendarControllerWebMvcTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var calendarService: CalendarService

    @MockitoBean
    lateinit var calendarSyncService: CalendarSyncService

    private val userId = UUID.randomUUID()

    private fun asUser(): Authentication =
        UsernamePasswordAuthenticationToken(AuthPrincipal(userId, "jiwoo@ieoseo.app"), null, emptyList())

    private fun <T> eqv(value: T): T {
        ArgumentMatchers.eq(value)
        return value
    }

    @Test
    fun `토큰 없이 연결 목록 조회는 401 UNAUTHORIZED`() {
        mockMvc.perform(get("/api/v1/calendar/connections"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `연결 목록은 envelope 로 provider 와 상태를 싣고 토큰은 노출하지 않는다`() {
        val conn = CalendarConnection(
            userId = userId,
            provider = CalendarProvider.GOOGLE,
            accessToken = "secret-token-should-not-leak",
            status = ConnectionStatus.CONNECTED,
        )
        `when`(calendarService.listConnections(eqv(userId))).thenReturn(listOf(conn))

        mockMvc.perform(get("/api/v1/calendar/connections").with(authentication(asUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].provider").value("GOOGLE"))
            .andExpect(jsonPath("$.data[0].status").value("CONNECTED"))
            .andExpect(jsonPath("$.data[0].accessToken").doesNotExist())
            .andExpect(jsonPath("$.data[0].refreshToken").doesNotExist())
    }

    @Test
    fun `connect 는 provider 경로를 대소문자 무시로 받아 연결을 반환한다`() {
        val conn = CalendarConnection(userId = userId, provider = CalendarProvider.GOOGLE)
        `when`(
            calendarService.connect(
                eqv(userId),
                eqv(CalendarProvider.GOOGLE),
                ArgumentMatchers.any(ConnectCalendarRequest::class.java) ?: ConnectCalendarRequest(),
            ),
        ).thenReturn(conn)

        mockMvc.perform(
            post("/api/v1/calendar/connections/google")
                .with(authentication(asUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"accessToken":"token-placeholder"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.provider").value("GOOGLE"))
    }

    @Test
    fun `미지원 provider 경로는 400 BAD_REQUEST`() {
        mockMvc.perform(
            post("/api/v1/calendar/connections/outlook")
                .with(authentication(asUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"accessToken":"token-placeholder"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `외부 일정 조회는 읽기 전용 필드와 출처를 envelope 로 반환한다`() {
        val event = ExternalEvent(
            userId = userId,
            provider = CalendarProvider.NOTION,
            externalId = "n1",
            title = "디자인 리뷰",
            date = LocalDate.of(2026, 6, 4),
            time = "14:30",
        )
        `when`(
            calendarService.externalEvents(
                eqv(userId),
                eqv(LocalDate.of(2026, 6, 1)),
                eqv(LocalDate.of(2026, 6, 30)),
            ),
        ).thenReturn(listOf(event))

        mockMvc.perform(
            get("/api/v1/calendar/external?from=2026-06-01&to=2026-06-30")
                .with(authentication(asUser())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].provider").value("NOTION"))
            .andExpect(jsonPath("$.data[0].title").value("디자인 리뷰"))
            .andExpect(jsonPath("$.data[0].time").value("14:30"))
            .andExpect(jsonPath("$.data[0].readOnly").value(true))
    }
}
