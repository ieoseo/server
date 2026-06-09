package app.ieoseo.server.debt.domain

/**
 * 시간부채 상태 전이 가드 (FRD 5.3).
 *
 * 유효 전이(server 권위):
 * ```
 * PENDING → CARRIED → RESOLVED
 *                   → OVERDUE → ABANDONED
 * ```
 * 사용자는 미해소/이월/연체 상태에서 탕감(ABANDONED)할 수 있고,
 * 원본 태스크 완료 시 PENDING/CARRIED/OVERDUE 에서 RESOLVED 로 해소된다.
 * RESOLVED/ABANDONED 는 종료 상태로 더 이상 전이하지 않는다.
 */
object DebtTransitions {

    private val allowed: Map<DebtStatus, Set<DebtStatus>> = mapOf(
        // PENDING 에서 자동 이월 시 같은 주 불가면 곧장 OVERDUE 로 넘어갈 수 있다.
        DebtStatus.PENDING to setOf(DebtStatus.CARRIED, DebtStatus.OVERDUE, DebtStatus.RESOLVED, DebtStatus.ABANDONED),
        DebtStatus.CARRIED to setOf(DebtStatus.RESOLVED, DebtStatus.OVERDUE, DebtStatus.ABANDONED),
        DebtStatus.OVERDUE to setOf(DebtStatus.CARRIED, DebtStatus.RESOLVED, DebtStatus.ABANDONED),
        DebtStatus.RESOLVED to emptySet(),
        DebtStatus.ABANDONED to emptySet(),
    )

    fun canTransition(from: DebtStatus, to: DebtStatus): Boolean =
        to in allowed.getOrDefault(from, emptySet())

    /** 유효 전이면 [to] 반환, 불법이면 [IllegalStateException] (409 CONFLICT). */
    fun require(from: DebtStatus, to: DebtStatus): DebtStatus {
        check(canTransition(from, to)) { "$from 에서 $to 로 부채 상태를 전이할 수 없습니다" }
        return to
    }
}
