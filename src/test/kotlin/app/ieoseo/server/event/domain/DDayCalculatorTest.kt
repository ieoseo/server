package app.ieoseo.server.event.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-Day 계산 도메인 로직 단위 테스트 (FRD 5.1).
 *
 * 행위 검증: T1 카운트다운 라벨, T2 진행률 클램프, T3 시작/마감 D-N, 긴급도 단계,
 * 그리고 타입별 날짜 불변식 위반 시 예외.
 */
class DDayCalculatorTest {

    private val today = LocalDate.of(2026, 6, 4)

    // ---------- T1 D-Day ----------

    @Test
    fun `T1 미래 목표일은 D-n 라벨과 양수 남은일수를 반환한다`() {
        val event = event(EventType.T1_DDAY, date = today.plusDays(10))

        val result = DDayCalculator.calculate(event, today)

        assertEquals(10, result.daysRemaining)
        assertEquals("D-10", result.label)
    }

    @Test
    fun `T1 당일은 D-Day 라벨과 0 남은일수를 반환한다`() {
        val event = event(EventType.T1_DDAY, date = today)

        val result = DDayCalculator.calculate(event, today)

        assertEquals(0, result.daysRemaining)
        assertEquals("D-Day", result.label)
    }

    @Test
    fun `T1 경과한 목표일은 D+n 라벨과 음수 남은일수를 반환한다`() {
        val event = event(EventType.T1_DDAY, date = today.minusDays(3))

        val result = DDayCalculator.calculate(event, today)

        assertEquals(-3, result.daysRemaining)
        assertEquals("D+3", result.label)
    }

    @Test
    fun `T1 은 date 가 없으면 예외를 던진다`() {
        val event = event(EventType.T1_DDAY, date = null)

        assertThrows<IllegalArgumentException> { DDayCalculator.calculate(event, today) }
    }

    // ---------- 긴급도 ----------

    @Test
    fun `3일 이내 남으면 긴급도는 high`() {
        val event = event(EventType.T1_DDAY, date = today.plusDays(3))
        assertEquals(Urgency.HIGH, DDayCalculator.calculate(event, today).urgency)
    }

    @Test
    fun `7일 이내 남으면 긴급도는 mid`() {
        val event = event(EventType.T1_DDAY, date = today.plusDays(7))
        assertEquals(Urgency.MID, DDayCalculator.calculate(event, today).urgency)
    }

    @Test
    fun `8일 이상 남으면 긴급도는 low`() {
        val event = event(EventType.T1_DDAY, date = today.plusDays(8))
        assertEquals(Urgency.LOW, DDayCalculator.calculate(event, today).urgency)
    }

    @Test
    fun `목표일이 지나면 긴급도는 past`() {
        val event = event(EventType.T1_DDAY, date = today.minusDays(1))
        assertEquals(Urgency.PAST, DDayCalculator.calculate(event, today).urgency)
    }

    // ---------- T2 진행률 ----------

    @Test
    fun `T2 기간 중간이면 진행률 백분율을 반환한다`() {
        // 10일 기간(0~10), 5일 경과 → 50%
        val event = event(
            EventType.T2_PROGRESS,
            startDate = today.minusDays(5),
            endDate = today.plusDays(5),
        )

        val result = DDayCalculator.calculate(event, today)

        assertEquals(50, result.progressPercent)
        assertEquals(EventPhase.IN_PROGRESS, result.phase)
    }

    @Test
    fun `T2 시작 전이면 진행률 0 으로 클램프하고 예정 상태`() {
        val event = event(
            EventType.T2_PROGRESS,
            startDate = today.plusDays(2),
            endDate = today.plusDays(12),
        )

        val result = DDayCalculator.calculate(event, today)

        assertEquals(0, result.progressPercent)
        assertEquals(EventPhase.UPCOMING, result.phase)
    }

    @Test
    fun `T2 종료 후면 진행률 100 으로 클램프하고 완료 상태`() {
        val event = event(
            EventType.T2_PROGRESS,
            startDate = today.minusDays(12),
            endDate = today.minusDays(2),
        )

        val result = DDayCalculator.calculate(event, today)

        assertEquals(100, result.progressPercent)
        assertEquals(EventPhase.FINISHED, result.phase)
    }

    @Test
    fun `T2 는 start 가 end 보다 늦으면 예외를 던진다`() {
        val event = event(
            EventType.T2_PROGRESS,
            startDate = today.plusDays(5),
            endDate = today.plusDays(1),
        )

        assertThrows<IllegalArgumentException> { DDayCalculator.calculate(event, today) }
    }

    // ---------- T3 기간 D-Day ----------

    @Test
    fun `T3 시작 전이면 시작까지와 마감까지 D-N 을 모두 반환한다`() {
        val event = event(
            EventType.T3_PERIOD_DDAY,
            startDate = today.plusDays(3),
            endDate = today.plusDays(10),
        )

        val result = DDayCalculator.calculate(event, today)

        assertEquals(3, result.daysToStart)
        assertEquals(10, result.daysToEnd)
        assertEquals(EventPhase.UPCOMING, result.phase)
    }

    @Test
    fun `T3 진행 중이면 시작까지는 0 이하, 마감까지는 양수`() {
        val event = event(
            EventType.T3_PERIOD_DDAY,
            startDate = today.minusDays(2),
            endDate = today.plusDays(4),
        )

        val result = DDayCalculator.calculate(event, today)

        assertEquals(-2, result.daysToStart)
        assertEquals(4, result.daysToEnd)
        assertEquals(EventPhase.IN_PROGRESS, result.phase)
    }

    @Test
    fun `T3 종료 후면 종료 상태이고 마감까지 음수`() {
        val event = event(
            EventType.T3_PERIOD_DDAY,
            startDate = today.minusDays(10),
            endDate = today.minusDays(1),
        )

        val result = DDayCalculator.calculate(event, today)

        assertEquals(-1, result.daysToEnd)
        assertEquals(EventPhase.FINISHED, result.phase)
    }

    @Test
    fun `T1 결과는 기간 전용 필드가 null`() {
        val event = event(EventType.T1_DDAY, date = today.plusDays(2))
        val result = DDayCalculator.calculate(event, today)
        assertNull(result.progressPercent)
        assertNull(result.daysToStart)
        assertTrue(result.daysRemaining != null)
    }

    private fun event(
        type: EventType,
        date: LocalDate? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): Event = Event(
        userId = UUID.randomUUID(),
        type = type,
        title = "테스트 이벤트",
        date = date,
        startDate = startDate,
        endDate = endDate,
    )
}
