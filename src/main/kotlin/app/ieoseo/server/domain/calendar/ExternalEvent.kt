package app.ieoseo.server.domain.calendar

import app.ieoseo.server.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.util.UUID

/**
 * 외부 캘린더에서 가져온 읽기 전용 일정 (FRD 4.5 / 5.7, 이슈 #59).
 *
 * 동기화([app.ieoseo.server.application.calendar.CalendarSyncService])가 provider 일정을
 * upsert 로 적재한다. 같은 일정의 재동기화는 `(user_id, provider, external_id)` 유니크로
 * 중복을 막고 제목/날짜/시각을 갱신한다.
 *
 * [readOnly] 는 항상 true(앱에서 편집 불가, 출처 표시 전용). 표현·뱃지는 클라이언트가 [provider]
 * 로 결정한다(server 는 원본 값만 보관).
 *
 * 날짜: [date] 는 LocalDate(ISO ymd `2026-06-04`). [time] 은 자유 문자열(예: `14:30`, null=종일).
 */
@Entity
@Table(
    name = "external_events",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_external_events_user_provider_external",
            columnNames = ["user_id", "provider", "external_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_external_events_user_id", columnList = "user_id"),
        Index(name = "idx_external_events_date", columnList = "date"),
    ],
)
class ExternalEvent(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    /** 소유자(users.id). 모든 조회는 이 값으로 스코프된다(인증-도메인 §2). */
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16, updatable = false)
    val provider: CalendarProvider,

    /** provider 가 부여한 원본 일정 id(upsert 키). */
    @Column(name = "external_id", nullable = false, length = 255, updatable = false)
    val externalId: String,

    @Column(name = "title", nullable = false, length = 500)
    var title: String,

    @Column(name = "date", nullable = false)
    var date: LocalDate,

    /** 시작 시각 표시 문자열(예: `14:30`). 종일 일정이면 null. */
    @Column(name = "time", length = 16)
    var time: String? = null,

    /** 항상 true — 외부 일정은 앱에서 읽기 전용(FRD 4.5). */
    @Column(name = "read_only", nullable = false)
    val readOnly: Boolean = true,
) : BaseEntity() {

    init {
        require(title.isNotBlank()) { "title 은 비어 있을 수 없다" }
        require(externalId.isNotBlank()) { "externalId 는 비어 있을 수 없다" }
    }

    /** 재동기화 시 변경 필드 갱신(불변 키는 유지). */
    fun applyFrom(other: ExternalEvent) {
        title = other.title
        date = other.date
        time = other.time
    }
}
