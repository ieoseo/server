package app.ieoseo.server.infrastructure.oauth

import app.ieoseo.server.domain.auth.AuthProvider

/**
 * 소셜 provider 토큰을 검증해 얻은 사용자 신원. 계약: 이슈 #37, `docs/06-백엔드/인증-도메인.md`.
 *
 * - [provider]: 검증한 소셜 provider(GOOGLE/APPLE/KAKAO).
 * - [providerId]: provider 가 부여한 고유 id(Google/Apple = ID token `sub`, Kakao = 사용자 id).
 * - [email]: provider 이메일(소셜 계정 식별·LOCAL 충돌 검사에 사용).
 */
data class OAuthIdentity(
    val provider: AuthProvider,
    val providerId: String,
    val email: String,
)

/**
 * 소셜 토큰 검증 실패. → 401 OAUTH_INVALID. 계약: `docs/05-API/auth.md`.
 *
 * 서명·iss·aud·exp 위반, provider API 오류, 미지원 provider 등 모든 검증 실패를 포괄한다.
 * 정보 노출 최소화를 위해 사용자에게는 일반 메시지로 매핑한다.
 */
class OAuthInvalidException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * provider 토큰을 검증해 [OAuthIdentity] 로 변환하는 검증자.
 *
 * 구현은 HTTP/JWKS 호출을 인터페이스 뒤로 추상화해 외부 의존 없이 테스트할 수 있어야 한다
 * (Google/Apple = ID token JWT 서명 검증, Kakao = 사용자 정보 API).
 * 검증 실패 시 [OAuthInvalidException] 를 던진다.
 */
interface OAuthVerifier {
    /** 이 검증자가 담당하는 provider. */
    val provider: AuthProvider

    /** provider 토큰(ID token 또는 access token)을 검증한다. 실패 시 [OAuthInvalidException]. */
    fun verify(token: String): OAuthIdentity
}

/**
 * provider → [OAuthVerifier] 매핑. 등록된 verifier 중 provider 가 일치하는 것을 찾는다.
 *
 * 미설정/미지원 provider 조회는 [OAuthInvalidException] 로 거부한다(설정 누락 노출 회피).
 */
class OAuthVerifierRegistry(verifiers: List<OAuthVerifier>) {
    private val byProvider: Map<AuthProvider, OAuthVerifier> = verifiers.associateBy { it.provider }

    fun forProvider(provider: AuthProvider): OAuthVerifier =
        byProvider[provider] ?: throw OAuthInvalidException("지원하지 않는 provider 입니다: $provider")
}
