package app.ieoseo.server.calendar.service

import app.ieoseo.server.global.exception.NotFoundException
import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.calendar.domain.ConnectionStatus
import app.ieoseo.server.calendar.domain.ExternalEvent
import app.ieoseo.server.calendar.repository.CalendarConnectionRepository
import app.ieoseo.server.calendar.repository.ExternalEventRepository
import app.ieoseo.server.calendar.dto.ConnectCalendarRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * 캘린더 연결 관리 서비스 (이슈 #59). controller 는 얇게 두고 규칙은 여기에 둔다.
 *
 * - connect: 사용자·provider 당 1개 보장(있으면 토큰/상태 갱신, 없으면 신규).
 * - listConnections / disconnect / externalEvents 는 모두 소유자(userId) 스코프.
 * - 동기화(외부 호출)는 [CalendarSyncService] 가 담당(관심사 분리).
 */
@Service
@Transactional(readOnly = true)
class CalendarService(
    private val connectionRepository: CalendarConnectionRepository,
    private val externalEventRepository: ExternalEventRepository,
) {
    fun listConnections(userId: UUID): List<CalendarConnection> =
        connectionRepository.findAllByUserId(userId)

    /**
     * provider 연결 등록/갱신(upsert). 토큰을 저장하고 상태를 CONNECTED 로 둔다.
     * 동기화는 별도 호출(`POST /sync`)로 수행한다.
     */
    @Transactional
    fun connect(userId: UUID, provider: CalendarProvider, request: ConnectCalendarRequest): CalendarConnection {
        val existing = connectionRepository.findByUserIdAndProvider(userId, provider).orElse(null)
        if (existing != null) {
            existing.accessToken = request.accessToken
            existing.refreshToken = request.refreshToken
            existing.expiresAt = request.expiresAt
            existing.status = ConnectionStatus.CONNECTED
            return existing
        }
        return connectionRepository.save(
            CalendarConnection(
                userId = userId,
                provider = provider,
                accessToken = request.accessToken,
                refreshToken = request.refreshToken,
                expiresAt = request.expiresAt,
                status = ConnectionStatus.CONNECTED,
            ),
        )
    }

    /** 연결 해제: 연결 레코드와 해당 provider 의 외부 일정을 함께 정리(소유자 스코프). */
    @Transactional
    fun disconnect(userId: UUID, provider: CalendarProvider) {
        val connection = connectionRepository.findByUserIdAndProvider(userId, provider)
            .orElseThrow { NotFoundException("CalendarConnection", provider) }
        externalEventRepository.deleteAllByUserIdAndProvider(userId, provider)
        connectionRepository.delete(connection)
    }

    /** 외부 일정 범위 조회(읽기 전용, 소유자 스코프). [from]~[to] 는 ISO ymd(포함). */
    fun externalEvents(userId: UUID, from: LocalDate, to: LocalDate): List<ExternalEvent> =
        externalEventRepository.findAllByUserIdAndDateBetween(userId, from, to)
}
