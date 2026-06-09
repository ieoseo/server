package app.ieoseo.server.global.security

import app.ieoseo.server.user.repository.UserRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
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
    ): JwtDecoder =
        // Supabase 비대칭 서명 키는 ES256(ECC P-256)이다(JWKS 의 alg=ES256). NimbusJwtDecoder
        // 기본 기대 알고리즘은 RS256 이라 명시하지 않으면 ES256 토큰을 거부(401)한다.
        // 레거시 호환 위해 RS256 도 함께 허용한다.
        NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build()

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
