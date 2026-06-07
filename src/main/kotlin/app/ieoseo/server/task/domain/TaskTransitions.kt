package app.ieoseo.server.task.domain

/**
 * 태스크 상태 머신 전이 가드 (FRD 5.2).
 *
 * 유효 전이(권위는 server, 클라이언트가 보낸 상태를 신뢰하지 않음):
 * ```
 * PENDING → TODAY → DONE
 * TODAY  → MISSED → CARRIED → DONE
 * CARRIED → OVERDUE → ABANDONED
 * ```
 * 활성/이월 상태(TODAY/MISSED/CARRIED/OVERDUE)에서는 사용자 행동으로 직접 이월(CARRIED)
 * 또는 탕감(ABANDONED)이 일어날 수 있어 그 전이도 허용한다.
 * DONE/ABANDONED 는 종료 상태로 더 이상 전이하지 않는다.
 */
object TaskTransitions {

    private val allowed: Map<TaskState, Set<TaskState>> = mapOf(
        TaskState.PENDING to setOf(TaskState.TODAY),
        TaskState.TODAY to setOf(TaskState.DONE, TaskState.MISSED, TaskState.CARRIED, TaskState.ABANDONED),
        TaskState.MISSED to setOf(TaskState.CARRIED, TaskState.ABANDONED),
        TaskState.CARRIED to setOf(TaskState.DONE, TaskState.OVERDUE, TaskState.ABANDONED),
        TaskState.OVERDUE to setOf(TaskState.ABANDONED, TaskState.DONE, TaskState.CARRIED),
        TaskState.DONE to emptySet(),
        TaskState.ABANDONED to emptySet(),
    )

    fun canTransition(from: TaskState, to: TaskState): Boolean =
        to in allowed.getOrDefault(from, emptySet())

    /**
     * 유효 전이면 [to] 를 반환하고, 불법 전이면 [IllegalStateException] 을 던진다.
     * 경계에서 409 CONFLICT 로 매핑한다.
     */
    fun require(from: TaskState, to: TaskState): TaskState {
        check(canTransition(from, to)) { "$from 에서 $to 로 전이할 수 없습니다" }
        return to
    }
}
