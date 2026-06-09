package app.ieoseo.server.event.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * 타입별 이벤트 날짜 검증 단위 테스트 (FRD 5.1 불변식).
 *
 * T1=date 필수(start/end 금지), T2/T3=start<=end 필수(date 금지).
 */
class EventValidationTest {

    private val day = LocalDate.of(2026, 6, 4)

    @Test
    fun `T1 은 date 가 있으면 통과한다`() {
        EventValidation.requireValidDates(EventType.T1_DDAY, date = day, startDate = null, endDate = null)
    }

    @Test
    fun `T1 은 date 가 없으면 예외`() {
        assertThrows<IllegalArgumentException> {
            EventValidation.requireValidDates(EventType.T1_DDAY, date = null, startDate = null, endDate = null)
        }
    }

    @Test
    fun `T1 에 기간 날짜가 섞이면 예외`() {
        assertThrows<IllegalArgumentException> {
            EventValidation.requireValidDates(EventType.T1_DDAY, date = day, startDate = day, endDate = day)
        }
    }

    @Test
    fun `T2 는 start 가 end 보다 같거나 이르면 통과`() {
        EventValidation.requireValidDates(
            EventType.T2_PROGRESS, date = null, startDate = day, endDate = day.plusDays(3),
        )
    }

    @Test
    fun `T2 는 start 가 end 보다 늦으면 예외`() {
        assertThrows<IllegalArgumentException> {
            EventValidation.requireValidDates(
                EventType.T2_PROGRESS, date = null, startDate = day.plusDays(3), endDate = day,
            )
        }
    }

    @Test
    fun `T3 은 start 나 end 가 없으면 예외`() {
        assertThrows<IllegalArgumentException> {
            EventValidation.requireValidDates(
                EventType.T3_PERIOD_DDAY, date = null, startDate = day, endDate = null,
            )
        }
    }

    @Test
    fun `T3 에 단일 date 가 섞이면 예외`() {
        assertThrows<IllegalArgumentException> {
            EventValidation.requireValidDates(
                EventType.T3_PERIOD_DDAY, date = day, startDate = day, endDate = day.plusDays(1),
            )
        }
    }

    @Test
    fun `메시지는 사용자 친화적이다`() {
        val ex = assertThrows<IllegalArgumentException> {
            EventValidation.requireValidDates(EventType.T1_DDAY, date = null, startDate = null, endDate = null)
        }
        assertEquals(true, ex.message?.isNotBlank())
    }
}
