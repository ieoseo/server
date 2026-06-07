package app.ieoseo.server.global.security

import app.ieoseo.server.global.common.ApiError
import app.ieoseo.server.global.common.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import tools.jackson.databind.ObjectMapper

/**
 * 인증되지 않은 보호 엔드포인트 접근 시 401 을 공통 envelope 로 응답한다.
 *
 * 계약: `docs/05-API/auth.md`(UNAUTHORIZED 401), `docs/05-API/README.md` 오류 형식.
 */
class RestAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        val body = ApiResponse.fail<Nothing>(
            ApiError(code = "UNAUTHORIZED", message = "인증이 필요합니다"),
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
