package app.ieoseo.server.presentation.auth

import app.ieoseo.server.domain.auth.AuthProvider
import app.ieoseo.server.domain.auth.User
import app.ieoseo.server.infrastructure.oauth.OAuthInvalidException
import app.ieoseo.server.infrastructure.security.JwtProvider
import app.ieoseo.server.infrastructure.security.SecurityConfig
import app.ieoseo.server.application.auth.AuthResult
import app.ieoseo.server.application.auth.AuthService
import app.ieoseo.server.application.auth.EmailLinkedLocalException
import app.ieoseo.server.application.auth.OAuthLoginResult
import app.ieoseo.server.application.auth.TokenPair
import app.ieoseo.server.application.auth.TokenService
import app.ieoseo.server.presentation.common.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * /auth/oauth/{provider} 슬라이스 테스트 (@WebMvcTest + 실제 SecurityConfig).
 *
 * AuthService 는 mock — verifier/HTTP 실호출 없이 200+isNew·401·409 응답을 검증한다.
 * 계약: `docs/05-API/auth.md`.
 */
@WebMvcTest(AuthController::class)
@Import(
    SecurityConfig::class,
    JwtProvider::class,
    GlobalExceptionHandler::class,
)
class OAuthControllerWebMvcTest {

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

    private fun loginResult(isNew: Boolean): OAuthLoginResult {
        val user = User(
            email = "user@gmail.com",
            nickname = "user",
            provider = AuthProvider.GOOGLE,
            providerId = "google-sub",
        )
        val tokens = TokenPair(
            userId = user.id,
            accessToken = jwtProvider.issueAccess(user.id, user.email),
            refreshToken = jwtProvider.issueRefresh(user.id, user.email),
        )
        return OAuthLoginResult(AuthResult(user, tokens), isNew)
    }

    @Test
    fun `oauth google 은 200 과 토큰 그리고 isNew 를 반환한다`() {
        doReturn(loginResult(isNew = true)).`when`(authService).oauthLogin(AuthProvider.GOOGLE, "google-id-token")

        val body = """{ "idToken": "google-id-token" }"""
        mockMvc.perform(post("/api/v1/auth/oauth/google").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.isNew").value(true))
            .andExpect(jsonPath("$.data.user.provider").value("GOOGLE"))
            .andExpect(jsonPath("$.data.tokens.accessToken").exists())
            .andExpect(jsonPath("$.data.tokens.tokenType").value("Bearer"))
    }

    @Test
    fun `oauth kakao 는 accessToken 본문으로 200 을 반환한다`() {
        doReturn(loginResult(isNew = false)).`when`(authService).oauthLogin(AuthProvider.KAKAO, "kakao-access-token")

        val body = """{ "accessToken": "kakao-access-token" }"""
        mockMvc.perform(post("/api/v1/auth/oauth/kakao").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isNew").value(false))
    }

    @Test
    fun `oauth 검증 실패는 401 OAUTH_INVALID`() {
        doThrow(OAuthInvalidException("invalid")).`when`(authService).oauthLogin(AuthProvider.GOOGLE, "bad-token")

        val body = """{ "idToken": "bad-token" }"""
        mockMvc.perform(post("/api/v1/auth/oauth/google").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("OAUTH_INVALID"))
    }

    @Test
    fun `oauth 이메일 LOCAL 충돌은 409 EMAIL_LINKED_LOCAL`() {
        doThrow(EmailLinkedLocalException("dup@gmail.com"))
            .`when`(authService).oauthLogin(AuthProvider.GOOGLE, "google-id-token")

        val body = """{ "idToken": "google-id-token" }"""
        mockMvc.perform(post("/api/v1/auth/oauth/google").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("EMAIL_LINKED_LOCAL"))
    }

    @Test
    fun `oauth 알 수 없는 provider 는 400 BAD_REQUEST`() {
        val body = """{ "idToken": "tok" }"""
        mockMvc.perform(post("/api/v1/auth/oauth/unknown").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
    }

    @Test
    fun `oauth 는 토큰이 본문에 없으면 400 BAD_REQUEST`() {
        val body = """{ }"""
        mockMvc.perform(post("/api/v1/auth/oauth/google").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
    }
}
