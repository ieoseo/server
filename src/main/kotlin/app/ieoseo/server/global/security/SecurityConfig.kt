package app.ieoseo.server.global.security

import app.ieoseo.server.user.repository.UserRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.ObjectMapper

/**
 * Supabase Auth(Resource Server) 보안 구성(ADR-0014). 세션 없음(STATELESS).
 * Supabase JWT 를 JWKS 로 검증 → AuthPrincipal 변환 → User upsert(UserProvisioningFilter).
 * 공개: 헬스체크. 그 외 전부 인증 필요.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun jwtDecoder(
        @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") jwkSetUri: String,
    ): JwtDecoder {
        // Supabase 비대칭 서명 키는 ES256(ECC P-256)이다(JWKS 의 alg=ES256). NimbusJwtDecoder
        // 기본 기대 알고리즘은 RS256 이라 명시하지 않으면 ES256 토큰을 거부(401)한다.
        // 레거시 호환 위해 RS256 도 함께 허용한다.
        val decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build()
        // 서명·만료만으로는 부족하다 — 발급자(iss)를 프로젝트 URL 로 핀 고정해 방어를 강화한다.
        // JWKS URI 형식이 예상과 다르거나 비어 있으면(로컬 미설정) 발급자 검증은 생략한다(fail-safe).
        val issuer = supabaseIssuerFrom(jwkSetUri)
        if (issuer != null) {
            decoder.setJwtValidator(
                DelegatingOAuth2TokenValidator(JwtValidators.createDefault(), JwtIssuerValidator(issuer)),
            )
        }
        return decoder
    }

    @Bean
    fun supabaseJwtAuthenticationConverter(): SupabaseJwtAuthenticationConverter = SupabaseJwtAuthenticationConverter()

    @Bean
    fun userProvisioningFilter(userRepositoryProvider: ObjectProvider<UserRepository>): UserProvisioningFilter =
        UserProvisioningFilter(userRepositoryProvider)

    @Bean
    fun restAuthenticationEntryPoint(objectMapper: ObjectMapper): RestAuthenticationEntryPoint =
        RestAuthenticationEntryPoint(objectMapper)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        converter: SupabaseJwtAuthenticationConverter,
        provisioningFilter: UserProvisioningFilter,
        entryPoint: RestAuthenticationEntryPoint,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/health/**", "/actuator/health", "/api/v1/health").permitAll()
                    // Google Calendar OAuth 콜백은 브라우저 리다이렉트라 공개(사용자 식별은 state, 이슈 #9).
                    .requestMatchers("/api/v1/calendar/oauth/**").permitAll()
                    // Swagger/OpenAPI 문서는 공개(prod 는 springdoc.*.enabled=false 로 비활성).
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { rs ->
                rs.jwt { it.jwtAuthenticationConverter(converter) }
                rs.authenticationEntryPoint(entryPoint)
            }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint) }
            .addFilterAfter(provisioningFilter, BearerTokenAuthenticationFilter::class.java)
        return http.build()
    }
}

/**
 * Supabase JWKS URI 에서 발급자(iss)를 도출한다.
 * `https://<ref>.supabase.co/auth/v1/.well-known/jwks.json` → `https://<ref>.supabase.co/auth/v1`.
 * 비어 있거나 예상 접미사로 끝나지 않으면 null(발급자 검증 생략, fail-safe).
 */
internal fun supabaseIssuerFrom(jwkSetUri: String): String? {
    val suffix = "/.well-known/jwks.json"
    if (jwkSetUri.isBlank() || !jwkSetUri.endsWith(suffix)) return null
    return jwkSetUri.removeSuffix(suffix)
}
