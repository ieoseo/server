package app.ieoseo.server.infrastructure.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

/**
 * Spring Security stateless 구성. 계약: `docs/05-API/auth.md` 보호 정책.
 *
 * - 세션 없음(STATELESS), CSRF off(비브라우저 토큰 클라이언트), httpBasic/formLogin off.
 * - 공개: `/api/v1/auth/signup|login|refresh|oauth/{provider}`, 헬스 체크.
 * - 인증 필요: `/api/v1/auth/me` 및 그 하위(프로필 수정·탈퇴·설정), `/api/v1/auth/logout`,
 *   events/tasks/debts/notifications 전체.
 * - 미인증 → [RestAuthenticationEntryPoint] 가 401 envelope 응답.
 * - access 검증은 [JwtAuthenticationFilter] 가 인증 필터 앞에서 수행.
 *
 * events/tasks/debts 는 인증 필수(#30) — 요청 주체(userId)로 소유권 스코프
 * (인증-도메인 §2). 타인 리소스는 service 가 404(NotFoundException)로 회피한다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun jwtAuthenticationFilter(jwtProvider: JwtProvider): JwtAuthenticationFilter =
        JwtAuthenticationFilter(jwtProvider)

    /**
     * Filter 빈은 서블릿 컨테이너에 자동 등록되어 보안 체인 밖에서도 한 번 실행된다.
     * 그러면 OncePerRequestFilter 중복 가드 탓에 보안 체인 안에서는 인증이 건너뛰어진다.
     * 자동 등록을 비활성화해 오직 SecurityFilterChain 안에서만 동작하게 한다.
     */
    @Bean
    fun jwtFilterRegistration(
        filter: JwtAuthenticationFilter,
    ): FilterRegistrationBean<JwtAuthenticationFilter> {
        val registration = FilterRegistrationBean(filter)
        registration.isEnabled = false
        return registration
    }

    @Bean
    fun restAuthenticationEntryPoint(objectMapper: ObjectMapper): RestAuthenticationEntryPoint =
        RestAuthenticationEntryPoint(objectMapper)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
        authenticationEntryPoint: RestAuthenticationEntryPoint,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/v1/auth/signup",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/oauth/**",
                    ).permitAll()
                    // 헬스체크는 공개(배포 플랫폼의 health probe 용 — HealthCheckController 는 /health/check).
                    .requestMatchers("/health/**", "/actuator/health", "/api/v1/health").permitAll()
                    // me·프로필 수정·탈퇴·설정(/me, /me/settings) + logout 은 인증 필수(#56).
                    .requestMatchers("/api/v1/auth/me", "/api/v1/auth/me/**", "/api/v1/auth/logout").authenticated()
                    // events/tasks/debts/notifications/calendar 는 인증 필수 — 요청 주체로 소유권 스코프(#30, #46, #59).
                    .requestMatchers(
                        "/api/v1/events/**",
                        "/api/v1/tasks/**",
                        "/api/v1/debts/**",
                        "/api/v1/notifications/**",
                        "/api/v1/calendar/**",
                    ).authenticated()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(authenticationEntryPoint) }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
