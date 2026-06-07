package app.ieoseo.server.domain.event

import java.time.LocalDate

/**
 * 타입별 이벤트 날짜 불변식 검증 (FRD 5.1).
 *
 * - [EventType.T1_DDAY]        : [date] 필수, 기간 날짜(start/end) 금지
 * - [EventType.T2_PROGRESS]    : [startDate]<=[endDate] 필수, 단일 [date] 금지
 * - [EventType.T3_PERIOD_DDAY] : [startDate]<=[endDate] 필수, 단일 [date] 금지
 *
 * 위반 시 [IllegalArgumentException] 을 던진다(경계에서 400 BAD_REQUEST 로 매핑).
 * 도메인 규칙은 server 권위 — DTO 형식 검증(jakarta)과 별개로 조합 검증을 여기서 강제한다.
 */
object EventValidation {

    fun requireValidDates(
        type: EventType,
        date: LocalDate?,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ) {
        when (type) {
            EventType.T1_DDAY -> {
                requireNotNull(date) { "T1_DDAY 이벤트는 목표일(date)이 필요합니다" }
                require(startDate == null && endDate == null) {
                    "T1_DDAY 이벤트는 기간 날짜(startDate/endDate)를 가질 수 없습니다"
                }
            }

            EventType.T2_PROGRESS, EventType.T3_PERIOD_DDAY -> {
                require(date == null) { "기간 이벤트는 단일 date 를 가질 수 없습니다" }
                val start = requireNotNull(startDate) { "기간 이벤트는 startDate 가 필요합니다" }
                val end = requireNotNull(endDate) { "기간 이벤트는 endDate 가 필요합니다" }
                require(!start.isAfter(end)) { "startDate 는 endDate 보다 늦을 수 없습니다" }
            }
        }
    }

    /** 엔티티 기준 검증 헬퍼. */
    fun requireValidDates(event: Event) =
        requireValidDates(event.type, event.date, event.startDate, event.endDate)
}
