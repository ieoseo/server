package app.ieoseo.server.domain.task

/**
 * 태스크 상태 머신 (FRD 5.2).
 *
 * 전이:
 * PENDING → TODAY → DONE
 * TODAY → MISSED → CARRIED → (완료 시) DONE
 * CARRIED → OVERDUE → ABANDONED
 *
 * 상태 전이의 권위는 server 도메인 서비스에 있다(클라이언트 계산을 신뢰하지 않음).
 */
enum class TaskState {
    /** 예정됨 (날짜 도래 전) */
    PENDING,

    /** 오늘 해야 할 태스크 (활성) */
    TODAY,

    /** 완료 */
    DONE,

    /** 미완료 (하루 경과, 완료 못 함) */
    MISSED,

    /** 이월됨 (다른 날로 이동, 시간부채 전환) */
    CARRIED,

    /** 연체 (해당 주 내 미해소 부채) */
    OVERDUE,

    /** 포기 (명시적 탕감) */
    ABANDONED,
}
