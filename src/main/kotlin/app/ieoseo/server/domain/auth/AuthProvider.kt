package app.ieoseo.server.domain.auth

/**
 * 사용자 가입 경로(인증 provider). 계약: `docs/06-백엔드/인증-도메인.md` §1.
 *
 * - [LOCAL]: 이메일 + 비밀번호(BCrypt). `passwordHash` 필수.
 * - 그 외(소셜): provider 발급 고유 id(`providerId`) 필수, `(provider, providerId)` 유니크.
 */
enum class AuthProvider {
    LOCAL,
    GOOGLE,
    APPLE,
    KAKAO,
}
