package app.ieoseo.server.event.dto

import app.ieoseo.server.event.domain.DDayResult
import app.ieoseo.server.event.domain.Event
import app.ieoseo.server.event.domain.EventPhase
import app.ieoseo.server.event.domain.EventType
import app.ieoseo.server.event.domain.Urgency
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 이벤트 생성/수정 요청. 형식 검증은 DTO(jakarta)에서, 타입별 날짜 조합 검증은
 * service([app.ieoseo.server.event.domain.EventValidation])에서 수행한다.
 */
data class EventCreateRequest(
    @field:NotNull val type: EventType,
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:Size(max = 50) val category: String? = null,
    val date: LocalDate? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val pinned: Boolean = false,
    @field:Size(max = 1000) val memo: String? = null,
    @field:Size(max = 16) val color: String? = null,
) {
    fun toEntity(userId: UUID): Event = Event(
        userId = userId,
        type = type,
        title = title,
        category = category,
        date = date,
        startDate = startDate,
        endDate = endDate,
        pinned = pinned,
        memo = memo,
        color = color,
    )
}

data class EventUpdateRequest(
    @field:NotNull val type: EventType,
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:Size(max = 50) val category: String? = null,
    val date: LocalDate? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val pinned: Boolean = false,
    @field:Size(max = 1000) val memo: String? = null,
    @field:Size(max = 16) val color: String? = null,
)

/**
 * 이벤트 응답. D-Day/진행률 등 파생 계산은 [dday] 블록(server 권위 계산)에 싣는다.
 */
data class EventResponse(
    val id: UUID,
    val type: EventType,
    val title: String,
    val category: String?,
    val date: LocalDate?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val pinned: Boolean,
    val memo: String?,
    val color: String?,
    /** server 권위 D-Day 파생 계산(FRD 5.1). 계산 컨텍스트가 없으면 null. */
    val dday: DDaySummary?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(event: Event, dDay: DDayResult? = null): EventResponse = EventResponse(
            id = event.id,
            type = event.type,
            title = event.title,
            category = event.category,
            date = event.date,
            startDate = event.startDate,
            endDate = event.endDate,
            pinned = event.pinned,
            memo = event.memo,
            color = event.color,
            dday = dDay?.let(DDaySummary::from),
            createdAt = event.createdAt,
            updatedAt = event.updatedAt,
        )
    }
}

/**
 * D-Day 파생 계산 응답 블록(FRD 5.1). 타입에 따라 일부 필드만 채워진다.
 */
data class DDaySummary(
    val daysRemaining: Int?,
    val label: String?,
    val progressPercent: Int?,
    val daysToStart: Int?,
    val daysToEnd: Int?,
    val phase: EventPhase,
    val urgency: Urgency,
) {
    companion object {
        fun from(result: DDayResult): DDaySummary = DDaySummary(
            daysRemaining = result.daysRemaining,
            label = result.label,
            progressPercent = result.progressPercent,
            daysToStart = result.daysToStart,
            daysToEnd = result.daysToEnd,
            phase = result.phase,
            urgency = result.urgency,
        )
    }
}
