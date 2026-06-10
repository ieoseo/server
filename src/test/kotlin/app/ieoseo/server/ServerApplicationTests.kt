package app.ieoseo.server

import app.ieoseo.server.user.repository.UserRepository
import app.ieoseo.server.calendar.repository.CalendarConnectionRepository
import app.ieoseo.server.calendar.repository.ExternalEventRepository
import app.ieoseo.server.notification.repository.NotificationRepository
import app.ieoseo.server.event.repository.EventRepository
import app.ieoseo.server.settings.repository.UserSettingsRepository
import app.ieoseo.server.task.repository.TaskRepository
import app.ieoseo.server.debt.repository.TimeDebtRepository
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * 컨텍스트 로드 스모크 테스트.
 *
 * 실 DB 없이 부팅되도록 DataSource/JPA auto-config 를 제외하고
 * (testcontainers 미도입 — 이슈 #6 지시), JPA 리포지토리는 mock 빈으로 대체한다.
 * web/service 계층 빈 배선만 검증한다.
 */
@SpringBootTest
@ImportAutoConfiguration(
    exclude = [
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        DataJpaRepositoriesAutoConfiguration::class,
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
