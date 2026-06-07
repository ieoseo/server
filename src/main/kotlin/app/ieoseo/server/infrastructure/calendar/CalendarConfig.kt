package app.ieoseo.server.infrastructure.calendar

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 캘린더 동기화 클라이언트 구성 (이슈 #59). oauth 의 OAuthConfig 패턴과 동일.
 *
 * Google/Notion/Apple [CalendarClient] 는 각자 `@Component` 다. [CalendarClientRegistry] 는
 * 등록된 모든 클라이언트를 모아 provider 로 조회 가능하게 한다.
 */
@Configuration
class CalendarConfig {

    @Bean
    fun calendarClientRegistry(clients: List<CalendarClient>): CalendarClientRegistry =
        CalendarClientRegistry(clients)
}
