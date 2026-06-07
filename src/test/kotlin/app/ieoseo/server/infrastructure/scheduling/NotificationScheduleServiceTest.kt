package app.ieoseo.server.infrastructure.scheduling

import app.ieoseo.server.application.notification.NotificationService
import app.ieoseo.server.domain.auth.User
import app.ieoseo.server.domain.event.Event
import app.ieoseo.server.domain.event.EventType
import app.ieoseo.server.domain.task.Task
import app.ieoseo.server.domain.task.TaskState
import app.ieoseo.server.infrastructure.persistence.auth.UserRepository
import app.ieoseo.server.infrastructure.persistence.event.EventRepository
import app.ieoseo.server.infrastructure.persistence.task.TaskRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.util.UUID

/**
 * D-Day/스트릭 알림 잡 단위 테스트 (#55, FRD 5.6).
 *
 * 임박 D-Day 이벤트(1/3/5/7일 전)와 스트릭(7의 배수) 갱신을 감지해
 * [NotificationService] 의 중복 방지 알림 메서드를 호출한다.
 */
class NotificationScheduleServiceTest {

    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val eventRepository: EventRepository = mock(EventRepository::class.java)
    private val taskRepository: TaskRepository = mock(TaskRepository::class.java)
    private val notificationService: NotificationService = mock(NotificationService::class.java)

    private val service = NotificationScheduleService(
        userRepository,
        eventRepository,
        taskRepository,
        notificationService,
    )

    private val today = LocalDate.of(2026, 6, 5)
    private val userId = UUID.randomUUID()

    /** 서비스 스트릭 산출 윈도(NotificationScheduleService.streakWindowDays 와 동일). */
    private val streakWindowDays = 365L
    private val streakWindowStart = today.minusDays(streakWindowDays)

    /** 매처/리터럴 혼용 NPE 회피용: 모든 stub 은 리터럴 인자만 쓴다. */
    private fun stubDdayEvents(vararg events: Event) {
        `when`(eventRepository.findAllByUserIdAndType(userId, EventType.T1_DDAY, Pageable.unpaged()))
            .thenReturn(PageImpl(events.toList()))
    }

    private fun stubDoneTasks(tasks: List<Task>) {
        `when`(taskRepository.findAllByUserIdAndDateBetween(userId, streakWindowStart, today, Pageable.unpaged()))
            .thenReturn(PageImpl(tasks))
    }

    @Test
    fun `D-3 임박 이벤트는 daysRemaining=3 으로 D-Day 알림을 호출한다`() {
        val eventId = UUID.randomUUID()
        val event = t1Event(eventId, today.plusDays(3), "토익 시험")
        `when`(userRepository.findAll()).thenReturn(listOf(user()))
        stubDdayEvents(event)
        stubDoneTasks(emptyList()) // 스트릭 없음(완료 태스크 없음).

        service.run(today)

        verify(notificationService).notifyDdayIfAbsent(userId, eventId, "토익 시험", 3)
    }

    @Test
    fun `임계가 아닌 D-4 이벤트는 daysRemaining=4 로 호출되어 규칙이 걸러낸다`() {
        val eventId = UUID.randomUUID()
        val event = t1Event(eventId, today.plusDays(4), "면접")
        `when`(userRepository.findAll()).thenReturn(listOf(user()))
        stubDdayEvents(event)
        stubDoneTasks(emptyList())

        service.run(today)

        // 잡은 daysRemaining 을 그대로 넘기고, 임계 판단은 NotificationRules(서비스)가 한다.
        verify(notificationService).notifyDdayIfAbsent(userId, eventId, "면접", 4)
    }

    @Test
    fun `경과했거나 임박하지 않은 이벤트는 D-Day 알림을 호출하지 않는다`() {
        val eventId = UUID.randomUUID()
        val event = t1Event(eventId, today.plusDays(30), "먼 일정")
        `when`(userRepository.findAll()).thenReturn(listOf(user()))
        stubDdayEvents(event)
        stubDoneTasks(emptyList())

        service.run(today)

        // D-30 은 임박 범위(0..7) 밖, 완료 태스크 없음 → 어떤 알림도 트리거하지 않는다.
        verifyNoInteractions(notificationService)
    }

    @Test
    fun `연속 완료 스트릭이 7일이면 스트릭 알림을 호출한다`() {
        `when`(userRepository.findAll()).thenReturn(listOf(user()))
        stubDdayEvents()
        // today 포함 직전 7일 연속 DONE 태스크 존재(8일 전은 없음 → 스트릭=7).
        stubDoneTasks((0..6).map { doneTask(today.minusDays(it.toLong())) })

        service.run(today)

        verify(notificationService).notifyStreakIfAbsent(userId, 7)
    }

    private fun user(): User = User(
        id = userId,
        email = "u@example.com",
        nickname = "n",
    )

    private fun t1Event(id: UUID, date: LocalDate, title: String): Event = Event(
        id = id,
        userId = userId,
        type = EventType.T1_DDAY,
        title = title,
        date = date,
    )

    private fun doneTask(date: LocalDate): Task = Task(
        userId = userId,
        title = "done",
        estimatedMinutes = 30,
        date = date,
        state = TaskState.DONE,
    )
}
