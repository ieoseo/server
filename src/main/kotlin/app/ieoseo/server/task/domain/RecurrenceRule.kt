package app.ieoseo.server.task.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 반복 주기 종류 (FRD 5.4).
 *
 * - [NONE]: 단발(반복 없음).
 * - [WEEKLY]: 매주 — 요일 다중 집합.
 * - [MONTHLY]: 매월 — 일자(1~31).
 * - [YEARLY]: 매년 — 월/일.
 */
enum class RecurrenceFrequency { NONE, WEEKLY, MONTHLY, YEARLY }

/**
 * 태스크 반복 규칙 값 객체 (FRD 5.4).
 *
 * Task 에 `@Embedded` 로 합쳐지는 불변 값 객체. 주어진 anchor(원래 예정일)와 날짜 범위에 대해
 * 반복 인스턴스의 날짜 목록을 산출하는 **순수 함수** [expand] 를 제공한다(테스트 가능, server 권위).
 *
 * 영속화 표현:
 * - [weeklyDaysMask]: WEEKLY 요일 집합을 비트마스크로 보관(월=bit0 … 일=bit6). NONE/그 외엔 0.
 * - [monthDay]: MONTHLY 일자(1~31). 그 외엔 null.
 * - [yearMonth]/[yearDay]: YEARLY 월(1~12)/일(1~31). 그 외엔 null.
 *
 * 정책: 템플릿(반복 규칙) 수정은 **이후 생성분에만** 반영되고, 이미 생성된 인스턴스는 불변이다(FRD 5.4).
 */
@Embeddable
class RecurrenceRule(
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_frequency", nullable = false, length = 16)
    val frequency: RecurrenceFrequency = RecurrenceFrequency.NONE,

    /** WEEKLY 요일 비트마스크(월=bit0 … 일=bit6). 그 외 주기는 0. */
    @Column(name = "recurrence_weekly_days", nullable = false)
    val weeklyDaysMask: Int = 0,

    /** MONTHLY 일자(1~31). 그 외 주기는 null. */
    @Column(name = "recurrence_month_day")
    val monthDay: Int? = null,

    /** YEARLY 월(1~12). 그 외 주기는 null. */
    @Column(name = "recurrence_year_month")
    val yearMonth: Int? = null,

    /** YEARLY 일(1~31). 그 외 주기는 null. */
    @Column(name = "recurrence_year_day")
    val yearDay: Int? = null,
) {
    init {
        when (frequency) {
            RecurrenceFrequency.NONE -> Unit
            RecurrenceFrequency.WEEKLY ->
                require(weeklyDaysMask != 0) { "WEEKLY 반복은 최소 한 요일을 선택해야 한다" }
            RecurrenceFrequency.MONTHLY -> {
                val day = requireNotNull(monthDay) { "MONTHLY 반복은 일자가 필요하다" }
                require(day in 1..31) { "MONTHLY 일자는 1~31 이어야 한다(got=$day)" }
            }
            RecurrenceFrequency.YEARLY -> {
                val month = requireNotNull(yearMonth) { "YEARLY 반복은 월이 필요하다" }
                val day = requireNotNull(yearDay) { "YEARLY 반복은 일이 필요하다" }
                require(month in 1..12) { "YEARLY 월은 1~12 이어야 한다(got=$month)" }
                require(day in 1..31) { "YEARLY 일은 1~31 이어야 한다(got=$day)" }
                // 어떤 해에도 존재할 수 없는 월/일(예: 2월 30일, 4월 31일)은 거부한다.
                require(isEverValidMonthDay(month, day)) { "$month 월 ${day}일은 존재할 수 없는 날짜다" }
            }
        }
    }

    /** 반복 여부(NONE 이 아니면 true). */
    val isRecurring: Boolean get() = frequency != RecurrenceFrequency.NONE

    /** WEEKLY 선택 요일 집합(그 외 주기는 빈 집합). */
    val weeklyDays: Set<DayOfWeek>
        get() = DayOfWeek.entries.filter { isWeeklyBitSet(it) }.toSet()

    /**
     * [anchor](원래 예정일)을 기준으로 [rangeStart]~[rangeEnd](양끝 포함) 범위에서
     * 이 규칙이 만들어내는 인스턴스 날짜를 오름차순으로 산출한다(순수 함수).
     *
     * - anchor 이전 날짜는 만들지 않는다(과거로 펼치지 않음).
     * - 해당 월/연에 존재하지 않는 날짜(2월 31일·평년 2/29 등)는 스킵한다.
     */
    fun expand(anchor: LocalDate, rangeStart: LocalDate, rangeEnd: LocalDate): List<LocalDate> {
        if (rangeEnd.isBefore(rangeStart)) return emptyList()
        val from = maxOf(rangeStart, anchor)
        if (from.isAfter(rangeEnd)) return emptyList()

        return when (frequency) {
            RecurrenceFrequency.NONE ->
                if (!anchor.isBefore(rangeStart) && !anchor.isAfter(rangeEnd)) listOf(anchor) else emptyList()
            RecurrenceFrequency.WEEKLY -> expandWeekly(from, rangeEnd)
            RecurrenceFrequency.MONTHLY -> expandMonthly(monthDay!!, from, rangeEnd)
            RecurrenceFrequency.YEARLY -> expandYearly(yearMonth!!, yearDay!!, from, rangeEnd)
        }
    }

    private fun expandWeekly(from: LocalDate, rangeEnd: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(rangeEnd) }
            .filter { isWeeklyBitSet(it.dayOfWeek) }
            .toList()

    private fun expandMonthly(day: Int, from: LocalDate, rangeEnd: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var cursor = LocalDate.of(from.year, from.monthValue, 1)
        while (!cursor.isAfter(rangeEnd)) {
            val length = cursor.lengthOfMonth()
            if (day <= length) {
                val candidate = cursor.withDayOfMonth(day)
                if (!candidate.isBefore(from) && !candidate.isAfter(rangeEnd)) result.add(candidate)
            }
            cursor = cursor.plusMonths(1)
        }
        return result
    }

    private fun expandYearly(month: Int, day: Int, from: LocalDate, rangeEnd: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        for (year in from.year..rangeEnd.year) {
            if (day > lengthOfMonth(year, month)) continue // 평년 2/29 등 스킵
            val candidate = LocalDate.of(year, month, day)
            if (!candidate.isBefore(from) && !candidate.isAfter(rangeEnd)) result.add(candidate)
        }
        return result
    }

    private fun isWeeklyBitSet(dow: DayOfWeek): Boolean =
        (weeklyDaysMask and bitOf(dow)) != 0

    companion object {
        fun none(): RecurrenceRule = RecurrenceRule(frequency = RecurrenceFrequency.NONE)

        fun weekly(days: Set<DayOfWeek>): RecurrenceRule {
            require(days.isNotEmpty()) { "WEEKLY 반복은 최소 한 요일을 선택해야 한다" }
            val mask = days.fold(0) { acc, d -> acc or bitOf(d) }
            return RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY, weeklyDaysMask = mask)
        }

        fun monthly(day: Int): RecurrenceRule {
            require(day in 1..31) { "MONTHLY 일자는 1~31 이어야 한다(got=$day)" }
            return RecurrenceRule(frequency = RecurrenceFrequency.MONTHLY, monthDay = day)
        }

        fun yearly(month: Int, day: Int): RecurrenceRule {
            require(month in 1..12) { "YEARLY 월은 1~12 이어야 한다(got=$month)" }
            require(day in 1..31) { "YEARLY 일은 1~31 이어야 한다(got=$day)" }
            require(isEverValidMonthDay(month, day)) { "$month 월 ${day}일은 존재할 수 없는 날짜다" }
            return RecurrenceRule(frequency = RecurrenceFrequency.YEARLY, yearMonth = month, yearDay = day)
        }

        /** 월=bit0 … 일=bit6 (DayOfWeek.value 1~7 → bit 0~6). */
        private fun bitOf(dow: DayOfWeek): Int = 1 shl (dow.value - 1)

        /** 윤년을 포함해 어떤 해에든 존재할 수 있는 월/일인지(2월은 윤년 기준 29일까지 허용). */
        private fun isEverValidMonthDay(month: Int, day: Int): Boolean =
            day <= lengthOfMonth(2024, month) // 2024 는 윤년 — 2월 29일까지 허용

        private fun lengthOfMonth(year: Int, month: Int): Int =
            LocalDate.of(year, month, 1).lengthOfMonth()
    }
}
