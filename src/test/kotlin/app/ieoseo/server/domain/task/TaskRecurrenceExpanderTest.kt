package app.ieoseo.server.domain.task

import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 반복 태스크 인스턴스 펼치기 도메인 서비스 단위 테스트 (FRD 5.4).
 *
 * 템플릿 Task + 날짜 범위 → 구체 Task 인스턴스 목록(순수 함수).
 * - 인스턴스는 각 발생일을 [Task.date] 로 갖고, 상태는 PENDING(server 권위).
 * - 인스턴스 자체는 반복하지 않는다(recurrence = NONE) — 무한 재펼침 방지.
 * - 제목·예상시간·카테고리·eventId 는 템플릿에서 복사한다.
 */
class TaskRecurrenceExpanderTest {

    private val owner = UUID.randomUUID()

    private fun template(date: LocalDate, rule: RecurrenceRule): Task = Task(
        userId = owner,
        title = "영어 단어",
        estimatedMinutes = 30,
        date = date,
        category = "어학",
        recurrence = rule,
    )

    @Test
    fun `WEEKLY 월수금 템플릿을 한 주 범위로 펼치면 3개 인스턴스가 나온다`() {
        val rule = RecurrenceRule.weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val tpl = template(LocalDate.of(2026, 6, 1), rule)

        val instances = TaskRecurrenceExpander.expand(tpl, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7))

        assertEquals(
            listOf(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 5)),
            instances.map { it.date },
        )
    }

    @Test
    fun `펼친 인스턴스는 템플릿 속성을 복사하고 PENDING 비반복이다`() {
        val rule = RecurrenceRule.weekly(setOf(DayOfWeek.MONDAY))
        val tpl = template(LocalDate.of(2026, 6, 1), rule)

        val instances = TaskRecurrenceExpander.expand(tpl, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 14))

        assertTrue(instances.isNotEmpty())
        instances.forEach { inst ->
            assertEquals(owner, inst.userId)
            assertEquals("영어 단어", inst.title)
            assertEquals(30, inst.estimatedMinutes)
            assertEquals("어학", inst.category)
            assertEquals(TaskState.PENDING, inst.state)
            assertEquals(RecurrenceFrequency.NONE, inst.recurrence.frequency)
        }
        // 매주 월요일 → 06-01, 06-08 두 개.
        assertEquals(listOf(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 8)), instances.map { it.date })
    }

    @Test
    fun `NONE 템플릿은 범위 안이면 자기 자신 한 개로 펼쳐진다`() {
        val tpl = template(LocalDate.of(2026, 6, 3), RecurrenceRule.none())

        val instances = TaskRecurrenceExpander.expand(tpl, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertEquals(listOf(LocalDate.of(2026, 6, 3)), instances.map { it.date })
    }

    @Test
    fun `MONTHLY 31일 템플릿은 31일이 없는 달을 스킵한다`() {
        val tpl = template(LocalDate.of(2026, 1, 31), RecurrenceRule.monthly(31))

        val instances = TaskRecurrenceExpander.expand(tpl, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30))

        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31)), instances.map { it.date })
    }
}
