package app.ieoseo.server.infrastructure.oauth

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 소셜 OAuth 검증자 구성. 계약: 이슈 #37, `docs/05-API/auth.md`.
 *
 * Google/Apple verifier 는 client-id 설정과 [JwksKeyResolver] 를 주입해 빈으로 등록하고,
 * Kakao verifier 는 자체 `@Component` 다. [OAuthVerifierRegistry] 는 등록된 모든
 * [OAuthVerifier] 를 모아 provider 로 조회 가능하게 한다.
 */
@Configuration
@EnableConfigurationProperties(OAuthProperties::class)
class OAuthConfig {

    @Bean
    fun googleOAuthVerifier(
        properties: OAuthProperties,
        keyResolver: JwksKeyResolver,
    ): GoogleOAuthVerifier = GoogleOAuthVerifier(properties.google, keyResolver)

    @Bean
    fun appleOAuthVerifier(
        properties: OAuthProperties,
        keyResolver: JwksKeyResolver,
    ): AppleOAuthVerifier = AppleOAuthVerifier(properties.apple, keyResolver)

    @Bean
    fun oAuthVerifierRegistry(verifiers: List<OAuthVerifier>): OAuthVerifierRegistry =
        OAuthVerifierRegistry(verifiers)
}
