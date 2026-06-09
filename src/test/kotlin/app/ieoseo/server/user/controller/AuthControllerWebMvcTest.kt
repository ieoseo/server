package app.ieoseo.server.user.controller

import app.ieoseo.server.user.domain.User
import app.ieoseo.server.global.security.AuthPrincipal
import app.ieoseo.server.global.security.SecurityConfig
import app.ieoseo.server.user.service.AuthService
import app.ieoseo.server.settings.service.UpdateUserSettingsCommand
import app.ieoseo.server.settings.service.UserSettingsService
import app.ieoseo.server.settings.domain.UserSettings
import app.ieoseo.server.settings.domain.WeekStart
import app.ieoseo.server.global.exception.GlobalExceptionHandler
import org.junit.jupiter.api.Test
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * AuthController 슬라이스 테스트 (@WebMvcTest + 실제 SecurityConfig).
 *
 * 인증·토큰 발급은 Supabase Auth + Resource Server 가 담당(ADR-0014). 슬라이스 테스트는
 * JWT 검증을 우회하고 [authentication] postprocessor 로 [AuthPrincipal] 을 직접 주입한다
 * (서비스는 mock). 미인증 경로는 토큰 없이 401 을 검증한다. 계약: `docs/05-API/auth.md`.
 */
@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class, GlobalExceptionHandler::class)
class AuthControllerWebMvcTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var authService: AuthService

    @MockitoBean
    lateinit var userSettingsService: UserSettingsService

    private val userId = UUID.randomUUID()

    private fun asUser(): Authentication =
        UsernamePasswordAuthenticationToken(AuthPrincipal(userId, "jiwoo@ieoseo.app"), null, emptyList())

    /** 이메일 미제공 provider(Kakao 등): email=null, name=표시 이름. */
    private fun asKakaoUser(): Authentication =
        UsernamePasswordAuthenticationToken(AuthPrincipal(userId, email = null, name = "카카오지우"), null, emptyList())

    private fun user(nickname: String = "지우"): User =
        User(id = userId, email = "jiwoo@ieoseo.app", nickname = nickname)

    /** Kotlin non-null 파라미터용 Mockito any() 헬퍼(UUID). */
    private fun anyUuid(): UUID {
        org.mockito.ArgumentMatchers.any(UUID::class.java)
        return userId
    }

    /** Kotlin non-null 파라미터용 Mockito any() 헬퍼(설정 명령). */
    private fun anyCommand(): UpdateUserSettingsCommand {
        org.mockito.ArgumentMatchers.any(UpdateUserSettingsCommand::class.java)
        return UpdateUserSettingsCommand(
            autoCarry = true,
            dayDeadlineHour = 0,
            weekStart = WeekStart.MON,
            maxDailyMinutes = 480,
            pomodoroFocus = 25,
            pomodoroShortBreak = 5,
            pomodoroLongBreak = 15,
            completionSound = true,
        )
    }

    @Test
    fun `me 는 토큰 없이 접근하면 401 UNAUTHORIZED`() {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `me 는 인증 주체면 200 과 사용자 정보를 반환한다`() {
        `when`(authService.me(userId)).thenReturn(user())

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(asUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(userId.toString()))
            .andExpect(jsonPath("$.data.email").value("jiwoo@ieoseo.app"))
            .andExpect(jsonPath("$.data.nickname").value("지우"))
    }

    @Test
    fun `me 는 이메일 없는 사용자면 email 을 null 로 닉네임은 표시 이름으로 반환한다`() {
        // Kakao 등 이메일 미제공 provider: AuthPrincipal.name 으로 provisioning 된 닉네임을 노출
        `when`(authService.me(userId)).thenReturn(User(id = userId, email = null, nickname = "카카오지우"))

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(asKakaoUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(userId.toString()))
            .andExpect(jsonPath("$.data.email").value(null as String?))
            .andExpect(jsonPath("$.data.nickname").value("카카오지우"))
    }

    @Test
    fun `PATCH me 는 토큰 없이 접근하면 401 UNAUTHORIZED`() {
        mockMvc.perform(
            patch("/api/v1/auth/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "nickname": "새이름" }"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `PATCH me 는 인증 주체면 닉네임을 수정하고 200`() {
        `when`(authService.updateNickname(userId, "새이름")).thenReturn(user(nickname = "새이름"))

        mockMvc.perform(
            patch("/api/v1/auth/me")
                .with(authentication(asUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "nickname": "새이름" }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("새이름"))
    }

    @Test
    fun `PATCH me 는 빈 닉네임이면 400 VALIDATION_ERROR`() {
        mockMvc.perform(
            patch("/api/v1/auth/me")
                .with(authentication(asUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "nickname": "" }"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `DELETE me 는 토큰 없이 접근하면 401 UNAUTHORIZED`() {
        mockMvc.perform(delete("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `DELETE me 는 인증 주체면 탈퇴 처리하고 204`() {
        mockMvc.perform(delete("/api/v1/auth/me").with(authentication(asUser())))
            .andExpect(status().isNoContent)

        org.mockito.Mockito.verify(authService).withdraw(userId)
    }

    @Test
    fun `GET me settings 는 토큰 없이 접근하면 401 UNAUTHORIZED`() {
        mockMvc.perform(get("/api/v1/auth/me/settings"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `GET me settings 는 인증 주체면 설정을 반환한다`() {
        `when`(userSettingsService.get(userId)).thenReturn(UserSettings(userId = userId))

        mockMvc.perform(get("/api/v1/auth/me/settings").with(authentication(asUser())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.autoCarry").value(true))
            .andExpect(jsonPath("$.data.weekStart").value("MON"))
            .andExpect(jsonPath("$.data.maxDailyMinutes").value(480))
            .andExpect(jsonPath("$.data.pomodoroFocus").value(25))
    }

    @Test
    fun `PUT me settings 는 인증 주체면 설정을 저장하고 200`() {
        `when`(userSettingsService.update(anyUuid(), anyCommand()))
            .thenReturn(UserSettings(userId = userId, autoCarry = false, weekStart = WeekStart.SUN))

        val body = """
            { "autoCarry": false, "dayDeadlineHour": 2, "weekStart": "SUN", "maxDailyMinutes": 600,
              "pomodoroFocus": 50, "pomodoroShortBreak": 10, "pomodoroLongBreak": 30, "completionSound": false }
        """.trimIndent()
        mockMvc.perform(
            put("/api/v1/auth/me/settings")
                .with(authentication(asUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.autoCarry").value(false))
            .andExpect(jsonPath("$.data.weekStart").value("SUN"))
    }

    @Test
    fun `PUT me settings 는 범위 밖 값이면 400 VALIDATION_ERROR`() {
        val body = """
            { "autoCarry": true, "dayDeadlineHour": 99, "weekStart": "MON", "maxDailyMinutes": 480,
              "pomodoroFocus": 25, "pomodoroShortBreak": 5, "pomodoroLongBreak": 15, "completionSound": true }
        """.trimIndent()
        mockMvc.perform(
            put("/api/v1/auth/me/settings")
                .with(authentication(asUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
    }
}
