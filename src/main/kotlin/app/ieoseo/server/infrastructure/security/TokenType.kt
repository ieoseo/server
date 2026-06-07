package app.ieoseo.server.infrastructure.security

/**
 * 토큰 용도. JWT 의 커스텀 `type` 클레임으로 직렬화한다.
 * refresh 를 access 로 오용하는 것을 차단한다.
 */
enum class TokenType {
    ACCESS,
    REFRESH,
}
