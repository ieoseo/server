package app.ieoseo.server.calendar.oauth

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** Google Calendar OAuth 설정 프로퍼티 등록(이슈 #9). */
@Configuration
@EnableConfigurationProperties(GoogleOAuthProperties::class)
class CalendarOAuthConfig
