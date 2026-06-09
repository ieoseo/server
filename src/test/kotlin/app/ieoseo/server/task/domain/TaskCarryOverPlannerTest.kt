package app.ieoseo.server.task.domain

import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 자동 이월 우선순위 도메인 서비스 단위 테스트 (FRD 5.3).
 *
 * 우선순위:
 *   ① 같은 주 오늘 이후 중 예약 시간이 가장 적은 날
 *   ② 주말(토/일) 우선
 *   ③ 하루 총 8시간(480분) 이상인 날 스킵
 *   ④ 같은 주 불가 → 다음 주 월요일 + 연체(OVERDUE)
 *
 * 순수 함수로 테스트 가능하게 입력(예약 현황 맵)을 주입한다.
 * 기준 주: 월요일 시작(주간 시작 요일은 설정값이나 테스트는 월 시작 가정).
 */
class TaskCarryOverPlannerTest {

    private val maxDailyMinutes = 480 // 하루 최대 예약 8시간

    // 2026-06-01(월) ~ 2026-06-07(일) 이 한 주.
    private val monday = LocalDate.of(2026, 6, 1)
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)
    private val thursday = monday.plusDays(3)
    private val friday = monday.plusDays(4)
    private val saturday = monday.plusDays(5)
    private val sunday = monday.plusDays(6)
    private val nextMonday = monday.plusDays(7)

    @Test
    fun `같은 주 오늘 이후 중 예약이 가장 적은 날로 이월한다`() {
        // 오늘=화. 모든 잔여일에 부하가 있고 목=30 이 최소 → 목요일.
        val schedule = mapOf(
            wednesday to 120,
            thursday to 30,
            friday to 200,
            saturday to 100,
            sunday to 90,
        )

        val plan = plan(taskMinutes = 60, today = tuesday, schedule = schedule)

        assertEquals(thursday, plan.targetDate)
        assertFalse(plan.overdue)
    }

    @Test
    fun `예약이 같다면 주말을 우선한다`() {
        // 오늘=수. 목=60, 금=60, 토=60, 일=60 → 동률이면 주말(토) 우선.
        val schedule = mapOf(thursday to 60, friday to 60, saturday to 60, sunday to 60)

        val plan = plan(taskMinutes = 30, today = wednesday, schedule = schedule)

        assertEquals(saturday, plan.targetDate)
        assertFalse(plan.overdue)
    }

    @Test
    fun `하루 총 8시간 이상인 날은 스킵한다`() {
        // 오늘=목. 금=470(+30=500 초과) 스킵, 토=480 이미 초과 스킵, 일=0 → 일요일.
        val schedule = mapOf(friday to 470, saturday to 480, sunday to 0)

        val plan = plan(taskMinutes = 30, today = thursday, schedule = schedule)

        assertEquals(sunday, plan.targetDate)
        assertFalse(plan.overdue)
    }

    @Test
    fun `남은 모든 날이 8시간 초과면 다음 주 월요일로 연체 이월한다`() {
        // 오늘=금. 토=480, 일=460(+30=490 초과) → 같은 주 불가 → 다음 주 월 + OVERDUE.
        val schedule = mapOf(saturday to 480, sunday to 460)

        val plan = plan(taskMinutes = 30, today = friday, schedule = schedule)

        assertEquals(nextMonday, plan.targetDate)
        assertTrue(plan.overdue)
    }

    @Test
    fun `오늘이 일요일이라 같은 주에 이후 날이 없으면 다음 주 월요일로 연체 이월한다`() {
        val plan = plan(taskMinutes = 60, today = sunday, schedule = emptyMap())

        assertEquals(nextMonday, plan.targetDate)
        assertTrue(plan.overdue)
        assertEquals(DayOfWeek.MONDAY, plan.targetDate.dayOfWeek)
    }

    @Test
    fun `예약 정보가 없는 날은 0분으로 간주해 후보가 된다`() {
        // 오늘=월. 화~일 예약 없음 → 가장 적은(0) 중 주말 우선 → 토요일.
        val plan = plan(taskMinutes = 60, today = monday, schedule = emptyMap())

        assertEquals(saturday, plan.targetDate)
        assertFalse(plan.overdue)
    }

    private fun plan(
        taskMinutes: Int,
        today: LocalDate,
        schedule: Map<LocalDate, Int>,
    ): CarryOverPlan = TaskCarryOverPlanner.plan(
        taskMinutes = taskMinutes,
        today = today,
        scheduledMinutesByDate = schedule,
        maxDailyMinutes = maxDailyMinutes,
    )
}
