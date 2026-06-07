package app.ieoseo.server.domain.auth

/**
 * 사용자 계정 상태. 계약: `docs/06-백엔드/인증-도메인.md` §1.
 * 탈퇴 시 [WITHDRAWN] 으로 전이하며 refresh 토큰은 폐기한다.
 */
enum class UserStatus {
    ACTIVE,
    WITHDRAWN,
}
