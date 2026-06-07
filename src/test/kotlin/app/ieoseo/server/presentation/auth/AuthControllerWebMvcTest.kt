package app.ieoseo.server.presentation.auth

import app.ieoseo.server.domain.auth.AuthProvider
import app.ieoseo.server.domain.auth.User
import app.ieoseo.server.infrastructure.security.JwtProvider
import app.ieoseo.server.infrastructure.security.SecurityConfig
import app.ieoseo.server.application.auth.AuthResult
import app.ieoseo.server.application.auth.AuthService
import app.ieoseo.server.application.auth.EmailTakenException
import app.ieoseo.server.application.auth.InvalidCredentialsException
import app.ieoseo.server.application.auth.TokenPair
import app.ieoseo.server.application.auth.TokenService
import app.ieoseo.server.presentation.common.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * AuthController 슬라이스 테스트 (@WebMvcTest + 실제 SecurityConfig).
 *
 * 보안 필터 체인을 실제로 로드해 공개/보호 경로를 검증한다(AuthService 는 mock).
 * JwtProvider 가 요구하는 JwtProperties 는 SecurityConfig 의 @EnableConfigurationProperties +
 * src/test/resources/application.yaml 더미 시크릿에서 바인딩된다.
 * 계약: `docs/05-API/auth.md`.
 */
@WebMvcTest(AuthController::class)
@Import(
    SecurityConfig::class,
    JwtProvider::class,
    GlobalExceptionHandler::class,
)
class AuthControllerWebMvcTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtProvider: JwtProvider

    @MockitoBean
    lateinit var authService: AuthService

    @MockitoBean
    lateinit var tokenService: TokenService

    @MockitoBean
    lateinit var userSettingsService: app.ieoseo.server.application.settings.UserSettingsService

    /** Kotlin non-null 파라미터용 Mockito any() 헬퍼(UUID). */
    private fun anyUuid(): UUID {
        org.mockito.ArgumentMatchers.any(UUID::class.java)
        return UUID.randomUUID()
    }

    /** Kotlin non-null 파라미터용 Mockito any() 헬퍼(설정 명령). */
    private fun anyCommand(): app.ieoseo.server.application.settings.UpdateUserSettingsCommand {
        org.mockito.ArgumentMatchers.any(app.ieoseo.server.application.settings.UpdateUserSettingsCommand::class.java)
        return app.ieoseo.server.application.settings.UpdateUserSettingsCommand(
            autoCarry = true,
            dayDeadlineHour = 0,
            weekStart = app.ieoseo.server.domain.settings.WeekStart.MON,
            maxDailyMinutes = 480,
            pomodoroFocus = 25,
            pomodoroShortBreak = 5,
            pomodoroLongBreak = 15,
            completionSound = true,
        )
    }

    private fun authResult(): AuthResult {
        val user = User(
            email = "jiwoo@ieoseo.app",
            nickname = "지우",
            provider = AuthProvider.LOCAL,
            passwordHash = "\$2a\$10\$hash",
        )
        val tokens = TokenPair(
            userId = user.id,
            accessToken = jwtProvider.issueAccess(user.id, user.email),
            refreshToken = jwtProvider.issueRefresh(user.id, user.email),
        )
        return AuthResult(user, tokens)
    }

    @Test
    fun `signup 은 201 과 토큰 응답을 반환한다`() {
        `when`(authService.signup(anyString(), anyString(), anyString())).thenReturn(authResult())

        val body = """{ "email": "jiwoo@ieoseo.app", "password": "password123", "nickname": "지우" }"""
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.user.email").value("jiwoo@ieoseo.app"))
            .andExpect(jsonPath("$.data.user.provider").value("LOCAL"))
            .andExpect(jsonPath("$.data.tokens.accessToken").exists())
            .andExpect(jsonPath("$.data.tokens.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.tokens.expiresIn").value(1800))
    }

    @Test
    fun `signup 검증 실패(짧은 비밀번호)는 400 VALIDATION_ERROR`() {
        val body = """{ "email": "jiwoo@ieoseo.app", "password": "short", "nickname": "지우" }"""
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `signup 검증 실패(잘못된 이메일)는 400 VALIDATION_ERROR`() {
        val body = """{ "email": "not-an-email", "password": "password123", "nickname": "지우" }"""
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `signup 중복 이메일은 409 EMAIL_TAKEN`() {
        `when`(authService.signup(anyString(), anyString(), anyString()))
            .thenThrow(EmailTakenException("jiwoo@ieoseo.app"))

        val body = """{ "email": "jiwoo@ieoseo.app", "password": "password123", "nickname": "지우" }"""
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"))
    }

    @Test
    fun `login 은 200 과 토큰 응답을 반환한다`() {
        `when`(authService.login(anyString(), anyString())).thenReturn(authResult())

        val body = """{ "email": "jiwoo@ieoseo.app", "password": "password123" }"""
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.tokens.accessToken").exists())
    }

    @Test
    fun `login 실패는 401 INVALID_CREDENTIALS`() {
        `when`(authService.login(anyString(), anyString())).thenThrow(InvalidCredentialsException())

        val body = """{ "email": "jiwoo@ieoseo.app", "password": "wrong-password" }"""
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `me 는 토큰 없이 접근하면 401 UNAUTHORIZED`() {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `me 는 유효한 access 토큰이면 200 과 사용자 정보를 반환한다`() {
        val userId = UUID.randomUUID()
        val access = jwtProvider.issueAccess(userId, "jiwoo@ieoseo.app")
        val user = User(
            id = userId,
            email = "jiwoo@ieoseo.app",
            nickname = "지우",
            provider = AuthProvider.LOCAL,
            passwordHash = "\$2a\$10\$hash",
        )
        `when`(authService.me(userId)).thenReturn(user)

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer ${access.value}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(userId.toString()))
            .andExpect(jsonPath("$.data.email").value("jiwoo@ieoseo.app"))
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
    fun `PATCH me 는 유효 토큰이면 닉네임을 수정하고 200`() {
        val userId = UUID.randomUUID()
        val access = jwtProvider.issueAccess(userId, "jiwoo@ieoseo.app")
        val updated = User(
            id = userId,
            email = "jiwoo@ieoseo.app",
            nickname = "새이름",
            provider = AuthProvider.LOCAL,
            passwordHash = "\$2a\$10\$hash",
        )
        `when`(authService.updateNickname(userId, "새이름")).thenReturn(updated)

        mockMvc.perform(
            patch("/api/v1/auth/me")
                .header("Authorization", "Bearer ${access.value}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "nickname": "새이름" }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("새이름"))
    }

    @Test
    fun `PATCH me 는 빈 닉네임이면 400 VALIDATION_ERROR`() {
        val access = jwtProvider.issueAccess(UUID.randomUUID(), "jiwoo@ieoseo.app")

        mockMvc.perform(
            patch("/api/v1/auth/me")
                .header("Authorization", "Bearer ${access.value}")
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
    fun `DELETE me 는 유효 토큰이면 탈퇴 처리하고 204`() {
        val userId = UUID.randomUUID()
        val access = jwtProvider.issueAccess(userId, "jiwoo@ieoseo.app")

        mockMvc.perform(delete("/api/v1/auth/me").header("Authorization", "Bearer ${access.value}"))
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
    fun `GET me settings 는 유효 토큰이면 설정을 반환한다`() {
        val userId = UUID.randomUUID()
        val access = jwtProvider.issueAccess(userId, "jiwoo@ieoseo.app")
        `when`(userSettingsService.get(userId))
            .thenReturn(app.ieoseo.server.domain.settings.UserSettings(userId = userId))

        mockMvc.perform(get("/api/v1/auth/me/settings").header("Authorization", "Bearer ${access.value}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.autoCarry").value(true))
            .andExpect(jsonPath("$.data.weekStart").value("MON"))
            .andExpect(jsonPath("$.data.maxDailyMinutes").value(480))
            .andExpect(jsonPath("$.data.pomodoroFocus").value(25))
    }

    @Test
    fun `PUT me settings 는 유효 토큰이면 설정을 저장하고 200`() {
        val userId = UUID.randomUUID()
        val access = jwtProvider.issueAccess(userId, "jiwoo@ieoseo.app")
        org.mockito.Mockito.`when`(userSettingsService.update(anyUuid(), anyCommand()))
            .thenReturn(
                app.ieoseo.server.domain.settings.UserSettings(
                    userId = userId,
                    autoCarry = false,
                    weekStart = app.ieoseo.server.domain.settings.WeekStart.SUN,
                ),
            )

        val body = """
            { "autoCarry": false, "dayDeadlineHour": 2, "weekStart": "SUN", "maxDailyMinutes": 600,
              "pomodoroFocus": 50, "pomodoroShortBreak": 10, "pomodoroLongBreak": 30, "completionSound": false }
        """.trimIndent()
        mockMvc.perform(
            put("/api/v1/auth/me/settings")
                .header("Authorization", "Bearer ${access.value}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.autoCarry").value(false))
            .andExpect(jsonPath("$.data.weekStart").value("SUN"))
    }

    @Test
    fun `PUT me settings 는 범위 밖 값이면 400 VALIDATION_ERROR`() {
        val access = jwtProvider.issueAccess(UUID.randomUUID(), "jiwoo@ieoseo.app")

        val body = """
            { "autoCarry": true, "dayDeadlineHour": 99, "weekStart": "MON", "maxDailyMinutes": 480,
              "pomodoroFocus": 25, "pomodoroShortBreak": 5, "pomodoroLongBreak": 15, "completionSound": true }
        """.trimIndent()
        mockMvc.perform(
            put("/api/v1/auth/me/settings")
                .header("Authorization", "Bearer ${access.value}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
    }
}
