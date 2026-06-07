package app.ieoseo.server.infrastructure.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 소셜 OAuth provider 설정. 값은 `application.yaml`/환경변수에서 주입한다(시크릿 하드코딩 금지).
 *
 * 계약: 이슈 #37, `docs/05-API/auth.md`. client-id 는 ID token `aud` 검증과
 * Kakao 앱 식별에 쓰인다. 키/시크릿 자체는 `.env`(미커밋)로만 주입한다.
 */
@ConfigurationProperties(prefix = "ieoseo.oauth")
data class OAuthProperties(
    val google: Provider = Provider(),
    val apple: Provider = Provider(),
    val kakao: Provider = Provider(),
) {
    /** provider 단위 설정. [clientId] 미설정(빈 문자열)이면 해당 provider 는 검증을 거부한다. */
    data class Provider(
        val clientId: String = "",
    )
}
