package app.ieoseo.server.infrastructure.calendar

import app.ieoseo.server.domain.calendar.CalendarConnection
import app.ieoseo.server.domain.calendar.CalendarProvider
import app.ieoseo.server.domain.calendar.ExternalEvent
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Apple 캘린더 동기화 스텁 (이슈 #59, ADR-0010).
 *
 * 제약: Apple 은 서버에서 캘린더를 읽을 공개 REST API 가 없다(EventKit 은 온디바이스 전용,
 * CalDAV 는 앱 전용 비밀번호·복잡한 인증 필요). 따라서 본 트랙에서는 **미지원**으로 두고
 * 항상 빈 결과를 반환한다(연결 자체는 등록 가능하되 동기화는 0건).
 *
 * 향후 CalDAV/온디바이스 브리지 도입 시 이 구현만 교체한다. 상세 제약: ADR-0010·docs/05-API/calendar.md.
 */
@Component
class AppleCalendarClient : CalendarClient {

    override val provider = CalendarProvider.APPLE

    /** 서버 API 부재 — 빈 결과(예외 없이 graceful). */
    override fun fetchEvents(connection: CalendarConnection, from: LocalDate, to: LocalDate): List<ExternalEvent> =
        emptyList()
}
