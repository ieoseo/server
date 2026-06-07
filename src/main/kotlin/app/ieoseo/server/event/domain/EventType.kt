package app.ieoseo.server.event.domain

/**
 * D-Day 이벤트 타입 3종 (FRD 5.1).
 *
 * - [T1_DDAY]      단일 목표일 카운트다운 (D-n / D-Day / D+n)
 * - [T2_PROGRESS]  기간 진행률 (시작~종료, 경과/전체 비율)
 * - [T3_PERIOD_DDAY] 기간 D-Day (시작까지 D-N, 마감까지 D-M)
 */
enum class EventType {
    T1_DDAY,
    T2_PROGRESS,
    T3_PERIOD_DDAY,
}
