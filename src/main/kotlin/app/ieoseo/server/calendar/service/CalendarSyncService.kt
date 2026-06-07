package app.ieoseo.server.calendar.service

import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.ExternalEvent
import app.ieoseo.server.calendar.client.CalendarClientRegistry
import app.ieoseo.server.calendar.client.CalendarSyncException
import app.ieoseo.server.calendar.repository.CalendarConnectionRepository
import app.ieoseo.server.calendar.repository.ExternalEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 수동 동기화 결과(provider 단위). [imported] 는 신규+갱신 건수.
 */
data class ProviderSyncResult(
    val connection: CalendarConnection,
    val imported: Int,
)

/**
 * 외부 캘린더 수동 동기화 서비스 (이슈 #59, FRD 5.7).
 *
 * 각 연결에 대해 [CalendarClientRegistry] 로 provider 클라이언트를 골라 외부 일정을 가져오고,
 * `(user_id, provider, external_id)` 기준으로 [ExternalEvent] 를 **upsert**(중복 방지·갱신)한다.
 * 성공이면 연결을 markSynced, [CalendarSyncException] 이면 markSyncFailed(재인증 유도)로 둔다.
 *
 * 자동(스케줄) 동기화는 범위 외 — 수동 sync 만 제공한다(후속 트랙).
 */
@Service
@Transactional
class CalendarSyncService(
    private val connectionRepository: CalendarConnectionRepository,
    private val externalEventRepository: ExternalEventRepository,
    private val clientRegistry: CalendarClientRegistry,
) {
    /** 동기화 기본 조회 창(오늘 기준 과거/미래). */
    private val pastWindowDays = 30L
    private val futureWindowDays = 120L

    /**
     * 사용자의 모든 연결을 동기화한다. provider 별 결과 목록을 반환한다.
     * 개별 provider 실패는 전체를 막지 않는다(해당 연결만 SYNC_FAILED).
     */
    fun syncAll(userId: UUID, today: LocalDate = LocalDate.now()): List<ProviderSyncResult> =
        connectionRepository.findAllByUserId(userId).map { sync(it, today) }

    private fun sync(connection: CalendarConnection, today: LocalDate): ProviderSyncResult {
        val from = today.minusDays(pastWindowDays)
        val to = today.plusDays(futureWindowDays)
        return try {
            val fetched = clientRegistry.forProvider(connection.provider).fetchEvents(connection, from, to)
            val imported = fetched.sumOf { upsert(it) }
            connection.markSynced(Instant.now())
            ProviderSyncResult(connection, imported)
        } catch (_: CalendarSyncException) {
            connection.markSyncFailed()
            ProviderSyncResult(connection, imported = 0)
        }
    }

    /** 단일 외부 일정 upsert. 기존이면 변경 필드 갱신, 없으면 신규 저장. 반환 1(처리됨). */
    private fun upsert(incoming: ExternalEvent): Int {
        val existing = externalEventRepository.findByUserIdAndProviderAndExternalId(
            incoming.userId,
            incoming.provider,
            incoming.externalId,
        ).orElse(null)
        if (existing != null) {
            existing.applyFrom(incoming)
        } else {
            externalEventRepository.save(incoming)
        }
        return 1
    }

    /** connect 직후 동기화에 쓰는 단일 provider 동기화. */
    fun syncProvider(connection: CalendarConnection, today: LocalDate = LocalDate.now()): ProviderSyncResult =
        sync(connection, today)
}
