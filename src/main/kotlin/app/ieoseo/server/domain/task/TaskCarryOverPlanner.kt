package app.ieoseo.server.domain.task

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 자동 이월 계획 결과 (FRD 5.3).
 *
 * @property targetDate 이월 대상 날짜
 * @property overdue 같은 주 안에서 소화하지 못해 다음 주로 넘긴(연체) 경우 true
 */
data class CarryOverPlan(
    val targetDate: LocalDate,
    val overdue: Boolean,
)

/**
 * 시간 빌려쓰기 자동 이월 우선순위 산출 도메인 서비스 (FRD 5.3).
 *
 * 우선순위:
 *   ① 같은 주 오늘 이후 중 예약 시간이 가장 적은 날
 *   ② 동률이면 주말(토/일) 우선
 *   ③ 태스크를 더하면 하루 총 [maxDailyMinutes] 이상이 되는 날(또는 이미 초과한 날) 스킵
 *   ④ 같은 주에 둘 곳이 없으면 다음 주 월요일 + 연체(overdue=true)
 *
 * 순수 함수: 예약 현황([scheduledMinutesByDate])과 [today] 만으로 결정한다.
 * 이 계산은 클라이언트가 단독 수행하지 않는다(server 권위).
 */
object TaskCarryOverPlanner {

    fun plan(
        taskMinutes: Int,
        today: LocalDate,
        scheduledMinutesByDate: Map<LocalDate, Int>,
        maxDailyMinutes: Int,
    ): CarryOverPlan {
        val candidates = sameWeekDaysAfter(today)
            .filter { fitsWithinCap(it, taskMinutes, scheduledMinutesByDate, maxDailyMinutes) }

        if (candidates.isEmpty()) {
            return CarryOverPlan(targetDate = nextMonday(today), overdue = true)
        }

        val best = candidates.minWithOrNull(
            compareBy<LocalDate>(
                { scheduledMinutesByDate.getOrDefault(it, 0) }, // ① 예약 적은 날
                { if (isWeekend(it)) 0 else 1 }, // ② 주말 우선
                { it }, // 동률이면 빠른 날
            ),
        )!!

        return CarryOverPlan(targetDate = best, overdue = false)
    }

    /** 같은 ISO 주(월~일) 안에서 오늘 다음 날부터 일요일까지. */
    private fun sameWeekDaysAfter(today: LocalDate): List<LocalDate> {
        val weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return generateSequence(today.plusDays(1)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(weekEnd) }
            .toList()
    }

    private fun fitsWithinCap(
        date: LocalDate,
        taskMinutes: Int,
        schedule: Map<LocalDate, Int>,
        maxDailyMinutes: Int,
    ): Boolean = schedule.getOrDefault(date, 0) + taskMinutes <= maxDailyMinutes

    private fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    private fun nextMonday(today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
}
