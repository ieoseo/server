package app.ieoseo.server.debt.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 시간부채 "출처" 라벨 산출 (FRD 5.3, "미룬 시간").
 *
 * 부채가 생긴 [originDate] 를 today 기준으로 사람이 읽기 쉬운 한국어 라벨로 바꾼다.
 * 클라이언트 카드의 "…에서 발생" 문구에 쓰인다.
 *
 * 규칙:
 * - 이번 주(월~일) 안: 요일만 (`"월요일"`)
 * - 지난주 안: `"지난주 "` 접두사 + 요일 (`"지난주 금요일"`)
 * - 그보다 오래된 날: 월·일 라벨 (`"5월 18일"`) — 요일만으로는 헷갈리므로
 *
 * 순수 함수(저장하지 않음). 표시 전용이라 클라이언트가 단독 계산해도 무방하나,
 * 일관성과 단일 출처를 위해 server 가 응답에 채워 보낸다.
 */
object DebtLabels {

    private val WEEKDAYS_KO = mapOf(
        DayOfWeek.MONDAY to "월요일",
        DayOfWeek.TUESDAY to "화요일",
        DayOfWeek.WEDNESDAY to "수요일",
        DayOfWeek.THURSDAY to "목요일",
        DayOfWeek.FRIDAY to "금요일",
        DayOfWeek.SATURDAY to "토요일",
        DayOfWeek.SUNDAY to "일요일",
    )

    fun fromLabel(originDate: LocalDate, today: LocalDate = LocalDate.now()): String {
        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val thisWeekEnd = thisWeekStart.plusDays(6)
        val lastWeekStart = thisWeekStart.minusWeeks(1)

        val weekday = WEEKDAYS_KO.getValue(originDate.dayOfWeek)
        return when {
            !originDate.isBefore(thisWeekStart) && !originDate.isAfter(thisWeekEnd) -> weekday
            !originDate.isBefore(lastWeekStart) && originDate.isBefore(thisWeekStart) -> "지난주 $weekday"
            else -> "${originDate.monthValue}월 ${originDate.dayOfMonth}일"
        }
    }
}
