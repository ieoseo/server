package app.ieoseo.server.domain.task

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 태스크 상태 머신 전이 가드 단위 테스트 (FRD 5.2).
 *
 * 유효 전이만 허용하고 불법 전이는 [IllegalStateException] 을 던지는지 검증한다.
 * 유효 전이: PENDING→TODAY→DONE / TODAY→MISSED→CARRIED→DONE / CARRIED→OVERDUE→ABANDONED.
 */
class TaskTransitionsTest {

    @Test
    fun `PENDING 에서 TODAY 로 전이할 수 있다`() {
        assertTrue(TaskTransitions.canTransition(TaskState.PENDING, TaskState.TODAY))
    }

    @Test
    fun `TODAY 에서 DONE 으로 전이할 수 있다`() {
        assertTrue(TaskTransitions.canTransition(TaskState.TODAY, TaskState.DONE))
    }

    @Test
    fun `TODAY 에서 MISSED 로 전이할 수 있다`() {
        assertTrue(TaskTransitions.canTransition(TaskState.TODAY, TaskState.MISSED))
    }

    @Test
    fun `MISSED 에서 CARRIED 로 전이할 수 있다`() {
        assertTrue(TaskTransitions.canTransition(TaskState.MISSED, TaskState.CARRIED))
    }

    @Test
    fun `CARRIED 에서 DONE 으로 전이할 수 있다`() {
        assertTrue(TaskTransitions.canTransition(TaskState.CARRIED, TaskState.DONE))
    }

    @Test
    fun `CARRIED 에서 OVERDUE 로 전이할 수 있다`() {
        assertTrue(TaskTransitions.canTransition(TaskState.CARRIED, TaskState.OVERDUE))
    }

    @Test
    fun `OVERDUE 에서 ABANDONED 로 전이할 수 있다`() {
        assertTrue(TaskTransitions.canTransition(TaskState.OVERDUE, TaskState.ABANDONED))
    }

    @Test
    fun `PENDING 에서 DONE 으로 곧장 전이할 수 없다`() {
        assertFalse(TaskTransitions.canTransition(TaskState.PENDING, TaskState.DONE))
    }

    @Test
    fun `DONE 은 종료 상태라 어떤 전이도 불가하다`() {
        assertFalse(TaskTransitions.canTransition(TaskState.DONE, TaskState.TODAY))
        assertFalse(TaskTransitions.canTransition(TaskState.DONE, TaskState.CARRIED))
    }

    @Test
    fun `ABANDONED 는 종료 상태라 어떤 전이도 불가하다`() {
        assertFalse(TaskTransitions.canTransition(TaskState.ABANDONED, TaskState.CARRIED))
    }

    @Test
    fun `같은 상태로의 전이는 허용하지 않는다`() {
        assertFalse(TaskTransitions.canTransition(TaskState.TODAY, TaskState.TODAY))
    }

    @Test
    fun `require 는 유효 전이면 통과하고 새 상태를 반환한다`() {
        assertEquals(TaskState.DONE, TaskTransitions.require(TaskState.TODAY, TaskState.DONE))
    }

    @Test
    fun `require 는 불법 전이면 IllegalStateException 을 던진다`() {
        assertThrows<IllegalStateException> {
            TaskTransitions.require(TaskState.PENDING, TaskState.ABANDONED)
        }
    }
}
