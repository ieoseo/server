package app.ieoseo.server.infrastructure.security

/**
 * 토큰이 만료·위조·손상되었거나 폐기되어 검증에 실패했을 때 던진다.
 * 인증 흐름에서 401(UNAUTHORIZED / REFRESH_INVALID)로 매핑된다.
 */
class InvalidTokenException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
