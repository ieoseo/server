package app.ieoseo.server.calendar.dto

import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.calendar.domain.ConnectionStatus
import app.ieoseo.server.calendar.domain.ExternalEvent
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 캘린더 연결 등록/갱신 요청 (이슈 #59). 소셜 토큰 재사용 또는 수동 입력.
 *
 * 토큰은 민감 정보 — 요청 본문으로만 받고 응답에는 절대 싣지 않는다. Notion 은 [refreshToken]
 * 에 database id 를 보관한다(본 트랙 단순화, ADR-0010). Apple 은 미지원이라 토큰 없이 등록 가능.
 */
data class ConnectCalendarRequest(
    @field:Size(max = 2048) val accessToken: String? = null,
    @field:Size(max = 2048) val refreshToken: String? = null,
    val expiresAt: Instant? = null,
)

/**
 * 캘린더 연결 응답 (이슈 #59). **토큰은 포함하지 않는다**(민감 — provider/상태/동기화 시각만).
 */
data class CalendarConnectionResponse(
    val provider: CalendarProvider,
    val status: ConnectionStatus,
    val lastSyncedAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun from(connection: CalendarConnection): CalendarConnectionResponse = CalendarConnectionResponse(
            provider = connection.provider,
            status = connection.status,
            lastSyncedAt = connection.lastSyncedAt,
            createdAt = connection.createdAt,
        )
    }
}

/**
 * 외부 일정 응답(읽기 전용, FRD 4.5). 날짜는 ISO ymd, 시각은 `HH:mm`(종일이면 null).
 */
data class ExternalEventResponse(
    val id: UUID,
    val provider: CalendarProvider,
    val externalId: String,
    val title: String,
    val date: LocalDate,
    val time: String?,
    val readOnly: Boolean,
) {
    companion object {
        fun from(event: ExternalEvent): ExternalEventResponse = ExternalEventResponse(
            id = event.id,
            provider = event.provider,
            externalId = event.externalId,
            title = event.title,
            date = event.date,
            time = event.time,
            readOnly = event.readOnly,
        )
    }
}

/**
 * 수동 동기화 결과 응답. provider 별 가져온 건수와 최종 상태/시각을 싣는다.
 */
data class SyncResultResponse(
    val provider: CalendarProvider,
    val imported: Int,
    val status: ConnectionStatus,
    val syncedAt: Instant?,
)
