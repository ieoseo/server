package app.ieoseo.server.calendar.client

import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.calendar.domain.ExternalEvent
import java.time.LocalDate

/**
 * 외부 캘린더 일정 동기화 실패. 토큰 만료·권한 거부·provider API 오류·미지원 provider 등을
 * 포괄한다. 동기화 service 가 잡아 연결 상태를 SYNC_FAILED 로 기록한다(재인증 유도).
 */
class CalendarSyncException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * provider 캘린더에서 일정을 가져오는 클라이언트 (이슈 #59).
 *
 * 실제 HTTP 호출(Google Calendar API events.list / Notion API)은 이 인터페이스 뒤로 추상화한다
 * (oauth 의 KakaoUserClient 패턴과 동일). 그래야 외부 호출 없이 테스트할 수 있다(CI 외부호출 0).
 * 조회 실패 시 [CalendarSyncException] 을 던진다.
 *
 * 반환하는 [ExternalEvent] 는 아직 영속화 전 도메인 객체다(userId 는 연결 소유자로 채운다).
 */
interface CalendarClient {
    /** 이 클라이언트가 담당하는 provider. */
    val provider: CalendarProvider

    /**
     * [connection] 의 토큰으로 [from]~[to](ISO ymd, 포함) 구간 일정을 조회한다.
     * 실패 시 [CalendarSyncException].
     */
    fun fetchEvents(connection: CalendarConnection, from: LocalDate, to: LocalDate): List<ExternalEvent>
}

/**
 * provider → [CalendarClient] 매핑. 등록된 클라이언트 중 provider 가 일치하는 것을 찾는다
 * (oauth 의 OAuthVerifierRegistry 패턴과 동일).
 *
 * 미지원 provider 조회는 [CalendarSyncException] 으로 거부한다.
 */
class CalendarClientRegistry(clients: List<CalendarClient>) {
    private val byProvider: Map<CalendarProvider, CalendarClient> = clients.associateBy { it.provider }

    fun forProvider(provider: CalendarProvider): CalendarClient =
        byProvider[provider] ?: throw CalendarSyncException("지원하지 않는 캘린더 provider 입니다: $provider")
}
