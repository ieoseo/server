package app.ieoseo.server.debt.controller

import app.ieoseo.server.debt.dto.*

import app.ieoseo.server.global.security.AuthPrincipal
import app.ieoseo.server.global.security.SecurityConfig
import app.ieoseo.server.debt.domain.DebtStatus
import app.ieoseo.server.debt.service.TimeDebtService
import app.ieoseo.server.global.exception.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
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
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * DebtController 슬라이스 테스트 (@WebMvcTest + 실제 SecurityConfig).
 *
 * debts 는 인증 필수(#30) — 미인증 401 과 인증 주체 스코프, 목록/요약/이월/탕감 매핑을 확인한다(서비스는 mock).
 * 목록·액션 응답은 원본 태스크 제목(title)과 출처 라벨(fromLabel)을 포함한다(#41).
 */
@WebMvcTest(DebtController::class)
@Import(SecurityConfig::class, GlobalExceptionHandler::class)
class DebtControllerWebMvcTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var timeDebtService: TimeDebtService

    private val userId = UUID.randomUUID()

    private fun asUser(): Authentication =
        UsernamePasswordAuthenticationToken(AuthPrincipal(userId, "jiwoo@ieoseo.app"), null, emptyList())

    /** Kotlin non-null 파라미터용 eq() 헬퍼(매처가 null 을 반환해 NPE 나는 문제 회피). */
    private fun <T> eqv(value: T): T {
        eq(value)
        return value
    }

    /** Kotlin non-null 파라미터용 any() 헬퍼(매처를 기록하고 비-null 기본값을 반환). */
    private fun anyPageable(): Pageable {
        any<Pageable>()
        return Pageable.unpaged()
    }

    private fun anyLocalDate(): LocalDate {
        any<LocalDate>()
        return LocalDate.now()
    }

    private fun response(
        status: DebtStatus,
        title: String = "알고리즘 2문제",
        fromLabel: String = "월요일",
        carriedToDate: LocalDate? = null,
        minutes: Int = 60,
    ): DebtResponse = DebtResponse(
        id = UUID.randomUUID(),
        taskId = UUID.randomUUID(),
        title = title,
        fromLabel = fromLabel,
        minutes = minutes,
        originDate = LocalDate.of(2026, 6, 1),
        status = status,
        carriedToDate = carriedToDate,
        createdAt = Instant.parse("2026-06-02T00:00:00Z"),
        updatedAt = Instant.parse("2026-06-02T00:00:00Z"),
    )

    @Test
    fun `부채 목록은 제목과 출처 라벨을 포함해 직렬화한다`() {
        `when`(
            timeDebtService.findAllResponses(eqv(userId), anyPageable(), eqv<DebtStatus?>(null), anyLocalDate()),
        ).thenReturn(PageImpl(listOf(response(DebtStatus.PENDING))))

        mockMvc.perform(get("/api/v1/debts").with(authentication(asUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].title").value("알고리즘 2문제"))
            .andExpect(jsonPath("$.data[0].fromLabel").value("월요일"))
            .andExpect(jsonPath("$.data[0].minutes").value(60))
    }

    @Test
    fun `요약 조회는 envelope 와 byStatus 를 직렬화한다`() {
        `when`(timeDebtService.summary(userId)).thenReturn(
            DebtSummaryResponse(
                weekStart = LocalDate.of(2026, 6, 1),
                totalMinutes = 90,
                byStatus = mapOf(DebtStatus.PENDING to 60, DebtStatus.OVERDUE to 30),
                overdue = true,
            ),
        )

        mockMvc.perform(get("/api/v1/debts/summary").with(authentication(asUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalMinutes").value(90))
            .andExpect(jsonPath("$.data.overdue").value(true))
            .andExpect(jsonPath("$.data.byStatus.PENDING").value(60))
    }

    @Test
    fun `토큰 없이 접근하면 401 UNAUTHORIZED`() {
        mockMvc.perform(get("/api/v1/debts/summary"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `수동 이월은 제목과 CARRIED 부채를 반환한다`() {
        val id = UUID.randomUUID()
        `when`(timeDebtService.carry(eqv(userId), eqv(id), eqv(LocalDate.of(2026, 6, 7)), anyLocalDate()))
            .thenReturn(response(DebtStatus.CARRIED, carriedToDate = LocalDate.of(2026, 6, 7)))

        val body = """{ "toDate": "2026-06-07" }"""
        mockMvc.perform(
            post("/api/v1/debts/{id}/carry", id)
                .with(authentication(asUser()))
                .contentType(MediaType.APPLICATION_JSON).content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CARRIED"))
            .andExpect(jsonPath("$.data.title").value("알고리즘 2문제"))
            .andExpect(jsonPath("$.data.carriedToDate").value("2026-06-07"))
    }

    @Test
    fun `자동 이월은 제목과 CARRIED 부채를 반환한다`() {
        val id = UUID.randomUUID()
        `when`(timeDebtService.autoCarry(eqv(userId), eqv(id), anyLocalDate()))
            .thenReturn(response(DebtStatus.CARRIED, carriedToDate = LocalDate.of(2026, 6, 4)))

        mockMvc.perform(post("/api/v1/debts/{id}/auto-carry", id).with(authentication(asUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CARRIED"))
            .andExpect(jsonPath("$.data.title").value("알고리즘 2문제"))
            .andExpect(jsonPath("$.data.carriedToDate").value("2026-06-04"))
    }

    @Test
    fun `이월 요청에 toDate 가 없으면 400 으로 거부한다`() {
        val id = UUID.randomUUID()
        mockMvc.perform(
            post("/api/v1/debts/{id}/carry", id)
                .with(authentication(asUser()))
                .contentType(MediaType.APPLICATION_JSON).content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `탕감은 ABANDONED 부채를 반환한다`() {
        val id = UUID.randomUUID()
        `when`(timeDebtService.abandon(eqv(userId), eqv(id), anyLocalDate()))
            .thenReturn(response(DebtStatus.ABANDONED, minutes = 45))

        mockMvc.perform(post("/api/v1/debts/{id}/abandon", id).with(authentication(asUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("ABANDONED"))
    }
}
