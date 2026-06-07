package app.ieoseo.server.event.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 마감 임박 단계 (FRD 5.1 긴급도).
 *
 * 남은 일수 기준: 3일 이내 [HIGH] / 7일 이내 [MID] / 그 외 [LOW] / 경과 [PAST].
 */
enum class Urgency { HIGH, MID, LOW, PAST }

/**
 * 이벤트 진행 단계. T2/T3 의 예정·진행중·완료(종료) 표현에 쓴다.
 */
enum class EventPhase { UPCOMING, IN_PROGRESS, FINISHED }

/**
 * D-Day 계산 결과(server 권위 산출, 저장하지 않고 응답에만 싣는다).
 *
 * 타입에 따라 사용하는 필드가 다르다:
 * - T1: [daysRemaining], [label]
 * - T2: [progressPercent]
 * - T3: [daysToStart], [daysToEnd]
 * 공통: [phase], [urgency].
 */
data class DDayResult(
    val daysRemaining: Int? = null,
    val label: String? = null,
    val progressPercent: Int? = null,
    val daysToStart: Int? = null,
    val daysToEnd: Int? = null,
    val phase: EventPhase,
    val urgency: Urgency,
)

/**
 * D-Day 카운트다운/진행률/긴급도 계산 도메인 로직 (FRD 5.1).
 *
 * 타입별 날짜 불변식(T1=date, T2/T3=start<=end)을 강제하며 위반 시
 * [IllegalArgumentException] 을 던진다(경계에서 400 BAD_REQUEST 로 매핑).
 * 파생 값은 영속화하지 않고 server 가 매 응답마다 계산한다.
 */
object DDayCalculator {

    private const val HIGH_THRESHOLD_DAYS = 3L
    private const val MID_THRESHOLD_DAYS = 7L

    fun calculate(event: Event, today: LocalDate): DDayResult = when (event.type) {
        EventType.T1_DDAY -> calculateT1(event, today)
        EventType.T2_PROGRESS -> calculateT2(event, today)
        EventType.T3_PERIOD_DDAY -> calculateT3(event, today)
    }

    private fun calculateT1(event: Event, today: LocalDate): DDayResult {
        val date = requireNotNull(event.date) { "T1_DDAY 이벤트는 date 가 필요하다" }
        val daysRemaining = daysBetween(today, date)
        return DDayResult(
            daysRemaining = daysRemaining,
            label = label(daysRemaining),
            phase = phaseFromRemaining(daysRemaining),
            urgency = urgency(daysRemaining),
        )
    }

    private fun calculateT2(event: Event, today: LocalDate): DDayResult {
        val (start, end) = requireRange(event)
        val total = daysBetween(start, end).toDouble()
        val elapsed = daysBetween(start, today).toDouble()
        val percent = when {
            total <= 0.0 -> if (!today.isBefore(end)) 100 else 0
            else -> ((elapsed / total) * 100).toInt().coerceIn(0, 100)
        }
        return DDayResult(
            progressPercent = percent,
            phase = phaseFromRange(today, start, end),
            urgency = urgency(daysBetween(today, end)),
        )
    }

    private fun calculateT3(event: Event, today: LocalDate): DDayResult {
        val (start, end) = requireRange(event)
        val daysToEnd = daysBetween(today, end)
        return DDayResult(
            daysToStart = daysBetween(today, start),
            daysToEnd = daysToEnd,
            phase = phaseFromRange(today, start, end),
            urgency = urgency(daysToEnd),
        )
    }

    /** 두 날짜 사이 일수(from 기준 to 까지). 미래면 양수, 과거면 음수. */
    private fun daysBetween(from: LocalDate, to: LocalDate): Int =
        ChronoUnit.DAYS.between(from, to).toInt()

    private fun label(daysRemaining: Int): String = when {
        daysRemaining > 0 -> "D-$daysRemaining"
        daysRemaining == 0 -> "D-Day"
        else -> "D+${-daysRemaining}"
    }

    private fun phaseFromRemaining(daysRemaining: Int): EventPhase = when {
        daysRemaining > 0 -> EventPhase.UPCOMING
        daysRemaining == 0 -> EventPhase.IN_PROGRESS
        else -> EventPhase.FINISHED
    }

    private fun phaseFromRange(today: LocalDate, start: LocalDate, end: LocalDate): EventPhase = when {
        today.isBefore(start) -> EventPhase.UPCOMING
        today.isAfter(end) -> EventPhase.FINISHED
        else -> EventPhase.IN_PROGRESS
    }

    private fun urgency(daysRemaining: Int): Urgency = when {
        daysRemaining < 0 -> Urgency.PAST
        daysRemaining <= HIGH_THRESHOLD_DAYS -> Urgency.HIGH
        daysRemaining <= MID_THRESHOLD_DAYS -> Urgency.MID
        else -> Urgency.LOW
    }

    private fun requireRange(event: Event): Pair<LocalDate, LocalDate> {
        val start = requireNotNull(event.startDate) { "기간 이벤트는 startDate 가 필요하다" }
        val end = requireNotNull(event.endDate) { "기간 이벤트는 endDate 가 필요하다" }
        require(!start.isAfter(end)) { "startDate 는 endDate 보다 늦을 수 없다" }
        return start to end
    }
}
