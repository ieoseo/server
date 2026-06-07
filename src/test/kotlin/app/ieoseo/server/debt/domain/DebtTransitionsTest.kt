package app.ieoseo.server.debt.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 시간부채 상태 전이 가드 단위 테스트 (FRD 5.3).
 *
 * 전이: PENDING → CARRIED → RESOLVED / OVERDUE → ABANDONED.
 */
class DebtTransitionsTest {

    @Test
    fun `PENDING 에서 CARRIED 로 전이할 수 있다`() {
        assertTrue(DebtTransitions.canTransition(DebtStatus.PENDING, DebtStatus.CARRIED))
    }

    @Test
    fun `CARRIED 에서 RESOLVED 로 전이할 수 있다`() {
        assertTrue(DebtTransitions.canTransition(DebtStatus.CARRIED, DebtStatus.RESOLVED))
    }

    @Test
    fun `CARRIED 에서 OVERDUE 로 전이할 수 있다`() {
        assertTrue(DebtTransitions.canTransition(DebtStatus.CARRIED, DebtStatus.OVERDUE))
    }

    @Test
    fun `OVERDUE 에서 ABANDONED 로 전이할 수 있다`() {
        assertTrue(DebtTransitions.canTransition(DebtStatus.OVERDUE, DebtStatus.ABANDONED))
    }

    @Test
    fun `PENDING 에서 ABANDONED 로 탕감할 수 있다`() {
        assertTrue(DebtTransitions.canTransition(DebtStatus.PENDING, DebtStatus.ABANDONED))
    }

    @Test
    fun `RESOLVED 는 종료 상태라 전이 불가`() {
        assertFalse(DebtTransitions.canTransition(DebtStatus.RESOLVED, DebtStatus.CARRIED))
    }

    @Test
    fun `ABANDONED 는 종료 상태라 전이 불가`() {
        assertFalse(DebtTransitions.canTransition(DebtStatus.ABANDONED, DebtStatus.CARRIED))
    }

    @Test
    fun `PENDING 에서 OVERDUE 로 곧장 전이할 수 있다`() {
        // 자동 이월 시 같은 주에 둘 곳이 없으면 PENDING→OVERDUE.
        assertTrue(DebtTransitions.canTransition(DebtStatus.PENDING, DebtStatus.OVERDUE))
    }

    @Test
    fun `RESOLVED 에서 다른 상태로 전이 불가`() {
        assertFalse(DebtTransitions.canTransition(DebtStatus.RESOLVED, DebtStatus.OVERDUE))
    }

    @Test
    fun `require 는 불법 전이면 예외를 던진다`() {
        assertThrows<IllegalStateException> {
            DebtTransitions.require(DebtStatus.RESOLVED, DebtStatus.PENDING)
        }
    }
}
