package app.ieoseo.server.infrastructure.scheduling

import app.ieoseo.server.domain.auth.User
import app.ieoseo.server.domain.task.RecurrenceRule
import app.ieoseo.server.domain.task.Task
import app.ieoseo.server.domain.task.TaskState
import app.ieoseo.server.infrastructure.persistence.auth.UserRepository
import app.ieoseo.server.infrastructure.persistence.task.TaskRepository
import app.ieoseo.server.domain.task.RecurrenceFrequency
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

/**
 * 반복 인스턴스 생성 잡 단위 테스트 (#55, FRD 5.4).
 *
 * 반복 규칙이 있는 템플릿을 [app.ieoseo.server.domain.task.TaskRecurrenceExpander] 로 펼쳐
 * 다가오는 기간(today ~ today+horizon)의 구체 인스턴스를 만든다.
 * 이미 같은 날짜로 만든 인스턴스가 있으면 중복 생성하지 않는다.
 */
class RecurrenceInstanceServiceTest {

    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val taskRepository: TaskRepository = mock(TaskRepository::class.java)
    private val horizonDays = 14L

    private val service = RecurrenceInstanceService(userRepository, taskRepository, horizonDays)

    private val today = LocalDate.of(2026, 6, 1) // 월요일
    private val userId = UUID.randomUUID()

    @Test
    fun `매주 월수금 템플릿은 기간 내 발생일마다 인스턴스를 만든다`() {
        val template = template(
            date = today,
            minutes = 30,
            title = "운동",
            rule = RecurrenceRule.weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
        )
        `when`(userRepository.findAll()).thenReturn(listOf(user()))
        `when`(taskRepository.findAllByUserIdAndRecurrenceFrequencyNot(userId, RecurrenceFrequency.NONE))
            .thenReturn(listOf(template))
        // 기존 인스턴스 없음.
        `when`(taskRepository.findAllByUserIdAndDateBetween(userId, today, today.plusDays(horizonDays), Pageable.unpaged()))
            .thenReturn(PageImpl(emptyList()))

        val created = service.run(today)

        // today(월)~today+14: 월/수/금 = 6/1,6/3,6/5,6/8,6/10,6/12,6/15 = 7회.
        assertEquals(7, created)
        val saved = captureSaveAll()
        assertEquals(7, saved.size)
        assertEquals("운동", saved.first().title)
        // 인스턴스는 반복하지 않는다(무한 재펼침 방지).
        saved.forEach { assertEquals(false, it.recurrence.isRecurring) }
    }

    @Test
    fun `이미 생성된 날짜의 인스턴스는 중복 생성하지 않는다`() {
        val template = template(
            date = today,
            minutes = 30,
            title = "운동",
            rule = RecurrenceRule.weekly(setOf(DayOfWeek.MONDAY)),
        )
        `when`(userRepository.findAll()).thenReturn(listOf(user()))
        `when`(taskRepository.findAllByUserIdAndRecurrenceFrequencyNot(userId, RecurrenceFrequency.NONE))
            .thenReturn(listOf(template))
        // 6/1~6/15 의 월요일 후보: 6/1, 6/8, 6/15. 이 중 6/8 인스턴스는 이미 존재.
        val existing = Task(
            userId = userId,
            title = "운동",
            estimatedMinutes = 30,
            date = today.plusDays(7), // 6/8
            state = TaskState.PENDING,
        )
        `when`(taskRepository.findAllByUserIdAndDateBetween(userId, today, today.plusDays(horizonDays), Pageable.unpaged()))
            .thenReturn(PageImpl(listOf(existing)))

        val created = service.run(today)

        // 6/8 은 이미 있으니 6/1·6/15 두 건만 생성(중복 제외).
        assertEquals(2, created)
        val saved = captureSaveAll()
        assertEquals(listOf(today, today.plusDays(14)), saved.map { it.date })
    }

    @Test
    fun `생성할 인스턴스가 없으면 saveAll 을 호출하지 않는다`() {
        `when`(userRepository.findAll()).thenReturn(listOf(user()))
        `when`(taskRepository.findAllByUserIdAndRecurrenceFrequencyNot(userId, RecurrenceFrequency.NONE))
            .thenReturn(emptyList())

        val created = service.run(today)

        assertEquals(0, created)
        verify(taskRepository, never()).saveAll(anyList<Task>())
    }

    /** saveAll 에 넘긴 인스턴스를 캡처한다(제네릭 추론·strict null 회피용 헬퍼). */
    @Suppress("UNCHECKED_CAST")
    private fun captureSaveAll(): List<Task> {
        val captor = ArgumentCaptor.forClass(MutableList::class.java) as ArgumentCaptor<MutableList<Task>>
        verify(taskRepository).saveAll(captor.capture())
        return captor.value.toList()
    }

    private fun user(): User = User(
        id = userId,
        email = "u@example.com",
        nickname = "n",
        passwordHash = "\$2a\$10\$testtesttesttesttesttesttesttesttesttesttest", // 합성 BCrypt 형식
    )

    private fun template(
        date: LocalDate,
        minutes: Int,
        title: String,
        rule: RecurrenceRule,
    ): Task = Task(
        userId = userId,
        title = title,
        estimatedMinutes = minutes,
        date = date,
        state = TaskState.PENDING,
        recurrence = rule,
    )
}
