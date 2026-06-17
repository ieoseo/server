package app.ieoseo.server.task.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Task 도메인 불변식 단위 테스트 — 범위 날짜(#50).
 *
 * date 는 항상 존재하는 마감 앵커, startDate 는 선택(범위 시작). 있으면 `startDate <= date`.
 */
class TaskTest {

    private val owner = UUID.randomUUID()

    private fun task(date: LocalDate, startDate: LocalDate?) = Task(
        userId = owner,
        title = "x",
        estimatedMinutes = 30,
        date = date,
        startDate = startDate,
    )

    @Test
    fun `startDate 가 없으면 단일 태스크로 생성된다`() {
        val t = task(LocalDate.of(2026, 6, 4), null)
        assertNull(t.startDate)
    }

    @Test
    fun `startDate 가 date(마감) 이전이면 범위 태스크로 생성된다`() {
        val t = task(LocalDate.of(2026, 6, 7), LocalDate.of(2026, 6, 4))
        assertEquals(LocalDate.of(2026, 6, 4), t.startDate)
    }

    @Test
    fun `startDate 가 date 와 같으면 허용한다(하루짜리 범위)`() {
        val d = LocalDate.of(2026, 6, 4)
        assertEquals(d, task(d, d).startDate)
    }

    @Test
    fun `startDate 가 date(마감)보다 뒤면 거부한다`() {
        assertThrows<IllegalArgumentException> {
            task(LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 7))
        }
    }
}
