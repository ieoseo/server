package app.ieoseo.server.domain.debt

/**
 * 시간부채 상태 (FRD 5.3 / docs/05-API DebtStatus).
 *
 * 전이: PENDING → CARRIED → RESOLVED / OVERDUE → ABANDONED.
 *
 * - [PENDING]   생성됨(미해소). 자동/수동 이월 배정 전.
 * - [CARRIED]   이월 배정됨(특정 날짜로 옮겨짐). 이슈 #12 의 ASSIGNED 의미를 포함.
 * - [RESOLVED]  원본 태스크 완료로 해소.
 * - [OVERDUE]   같은 주 내 소화 실패로 다음 주로 넘김(연체).
 * - [ABANDONED] 사용자가 명시적으로 탕감(내려놓기).
 */
enum class DebtStatus {
    PENDING,
    CARRIED,
    RESOLVED,
    OVERDUE,
    ABANDONED,
}
