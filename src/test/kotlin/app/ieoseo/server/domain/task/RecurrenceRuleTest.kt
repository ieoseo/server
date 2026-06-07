package app.ieoseo.server.domain.task

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 반복 규칙 값 객체 단위 테스트 (FRD 5.4).
 *
 * - NONE: 반복 없음(단발).
 * - WEEKLY: 요일 다중 집합.
 * - MONTHLY: 매월 일자(1~31). 해당 월에 없는 일자는 그 달 스킵(예: 2월 31일).
 * - YEARLY: 매년 월/일. 윤년 2/29 는 평년 스킵.
 *
 * 펼치기(expand)는 순수 함수: 규칙 + 날짜 범위 → 인스턴스 날짜 목록.
 */
class RecurrenceRuleTest {

    @Test
    fun `NONE 규칙은 불변식이 단순하고 펼치면 범위 내 anchor 하루만 만든다`() {
        val rule = RecurrenceRule.none()
        assertEquals(RecurrenceFrequency.NONE, rule.frequency)

        val anchor = LocalDate.of(2026, 6, 3)
        val dates = rule.expand(anchor, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertEquals(listOf(anchor), dates)
    }

    @Test
    fun `NONE 규칙은 anchor 가 범위 밖이면 빈 목록을 만든다`() {
        val rule = RecurrenceRule.none()
        val anchor = LocalDate.of(2026, 5, 1)

        val dates = rule.expand(anchor, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertTrue(dates.isEmpty())
    }

    @Test
    fun `WEEKLY 다중 요일은 범위 안의 해당 요일을 모두 만든다`() {
        // 월·수·금 반복. 2026-06-01(월) ~ 06-07(일) 범위.
        val rule = RecurrenceRule.weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val anchor = LocalDate.of(2026, 6, 1) // 월

        val dates = rule.expand(anchor, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7))

        assertEquals(
            listOf(
                LocalDate.of(2026, 6, 1), // 월
                LocalDate.of(2026, 6, 3), // 수
                LocalDate.of(2026, 6, 5), // 금
            ),
            dates,
        )
    }

    @Test
    fun `WEEKLY 는 anchor 이전 날짜는 만들지 않는다`() {
        // anchor=수(06-03)면 같은 주 월(06-01)은 제외, 수·금만.
        val rule = RecurrenceRule.weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val anchor = LocalDate.of(2026, 6, 3)

        val dates = rule.expand(anchor, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7))

        assertEquals(listOf(LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 5)), dates)
    }

    @Test
    fun `WEEKLY 요일 집합은 비어 있을 수 없다`() {
        assertThrows<IllegalArgumentException> { RecurrenceRule.weekly(emptySet()) }
    }

    @Test
    fun `MONTHLY 일자는 범위 안 각 달의 해당 일을 만든다`() {
        // 매월 15일. 2026-06-10 ~ 2026-08-20 → 6/15, 7/15, 8/15.
        val rule = RecurrenceRule.monthly(15)
        val anchor = LocalDate.of(2026, 6, 15)

        val dates = rule.expand(anchor, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 8, 20))

        assertEquals(
            listOf(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 15)),
            dates,
        )
    }

    @Test
    fun `MONTHLY 31일은 해당 일이 없는 달을 스킵한다`() {
        // 매월 31일. 2026-01-31 ~ 2026-04-30 → 1/31, 3/31 (2월·4월 없음).
        val rule = RecurrenceRule.monthly(31)
        val anchor = LocalDate.of(2026, 1, 31)

        val dates = rule.expand(anchor, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30))

        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31)), dates)
    }

    @Test
    fun `MONTHLY 일자는 1에서 31 사이여야 한다`() {
        assertThrows<IllegalArgumentException> { RecurrenceRule.monthly(0) }
        assertThrows<IllegalArgumentException> { RecurrenceRule.monthly(32) }
    }

    @Test
    fun `YEARLY 월일은 범위 안 각 연도의 해당 날짜를 만든다`() {
        // 매년 8월 2일. 2026-01-01 ~ 2028-12-31 → 2026·2027·2028 8/2.
        val rule = RecurrenceRule.yearly(8, 2)
        val anchor = LocalDate.of(2026, 8, 2)

        val dates = rule.expand(anchor, LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31))

        assertEquals(
            listOf(LocalDate.of(2026, 8, 2), LocalDate.of(2027, 8, 2), LocalDate.of(2028, 8, 2)),
            dates,
        )
    }

    @Test
    fun `YEARLY 2월 29일은 평년을 스킵하고 윤년만 만든다`() {
        // 매년 2월 29일. 2024(윤)~2028(윤) → 2024, 2028 만.
        val rule = RecurrenceRule.yearly(2, 29)
        val anchor = LocalDate.of(2024, 2, 29)

        val dates = rule.expand(anchor, LocalDate.of(2024, 1, 1), LocalDate.of(2028, 12, 31))

        assertEquals(listOf(LocalDate.of(2024, 2, 29), LocalDate.of(2028, 2, 29)), dates)
    }

    @Test
    fun `YEARLY 월일 검증 - 잘못된 월일은 거부한다`() {
        assertThrows<IllegalArgumentException> { RecurrenceRule.yearly(13, 1) }
        assertThrows<IllegalArgumentException> { RecurrenceRule.yearly(2, 30) } // 2월 30일은 어떤 해도 불가
    }

    @Test
    fun `expand 는 anchor 이전과 범위 밖을 제외한다`() {
        // 매주 월요일. anchor=06-08(월). 범위 06-01~06-22 → 06-08, 06-15, 06-22 (06-01 제외).
        val rule = RecurrenceRule.weekly(setOf(DayOfWeek.MONDAY))
        val anchor = LocalDate.of(2026, 6, 8)

        val dates = rule.expand(anchor, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 22))

        assertEquals(
            listOf(LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 22)),
            dates,
        )
    }

    @Test
    fun `isRecurring 은 NONE 이면 false 그 외 true`() {
        assertFalse(RecurrenceRule.none().isRecurring)
        assertTrue(RecurrenceRule.weekly(setOf(DayOfWeek.MONDAY)).isRecurring)
        assertTrue(RecurrenceRule.monthly(1).isRecurring)
        assertTrue(RecurrenceRule.yearly(1, 1).isRecurring)
    }
}
