package app.ieoseo.server

import app.ieoseo.server.infrastructure.persistence.auth.UserRepository
import app.ieoseo.server.infrastructure.persistence.calendar.CalendarConnectionRepository
import app.ieoseo.server.infrastructure.persistence.calendar.ExternalEventRepository
import app.ieoseo.server.infrastructure.persistence.notification.NotificationRepository
import app.ieoseo.server.infrastructure.persistence.event.EventRepository
import app.ieoseo.server.infrastructure.persistence.settings.UserSettingsRepository
import app.ieoseo.server.infrastructure.persistence.task.TaskRepository
import app.ieoseo.server.infrastructure.persistence.debt.TimeDebtRepository
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * 컨텍스트 로드 스모크 테스트.
 *
 * 실 DB/Redis 없이 부팅되도록 DataSource/JPA/Redis auto-config 를 제외하고
 * (testcontainers 미도입 — 이슈 #6 지시), JPA 리포지토리는 mock 빈으로 대체한다.
 * web/service 계층 빈 배선만 검증한다.
 */
@SpringBootTest
@ImportAutoConfiguration(
    exclude = [
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        DataJpaRepositoriesAutoConfiguration::class,
        DataRedisAutoConfiguration::class,
        DataRedisRepositoriesAutoConfiguration::class,
    ],
)
class ServerApplicationTests {

    @MockitoBean
    lateinit var eventRepository: EventRepository

    @MockitoBean
    lateinit var taskRepository: TaskRepository

    @MockitoBean
    lateinit var timeDebtRepository: TimeDebtRepository

    @MockitoBean
    lateinit var userRepository: UserRepository

    @MockitoBean
    lateinit var notificationRepository: NotificationRepository

    @MockitoBean
    lateinit var userSettingsRepository: UserSettingsRepository

    @MockitoBean
    lateinit var calendarConnectionRepository: CalendarConnectionRepository

    @MockitoBean
    lateinit var externalEventRepository: ExternalEventRepository

    @Test
    fun contextLoads() {
    }
}
