package app.ieoseo.server.global.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI(Swagger) 문서 메타데이터. springdoc 이 이 [OpenAPI] 빈을 읽어 문서 상단 정보를 채운다.
 *
 * 노출: local 은 `/swagger-ui.html`·`/v3/api-docs` 공개(SecurityConfig permitAll),
 * prod 는 `springdoc.*.enabled=false`(application-prod.yml)로 비활성.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun ieoseoOpenApi(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("이어서 API")
                .description("이어서 백엔드 API — D-Day·할 일·집중 타이머·시간 빌려쓰기")
                .version("v1"),
        )
}
