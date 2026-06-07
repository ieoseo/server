package app.ieoseo.server.infrastructure.persistence.calendar

import app.ieoseo.server.domain.calendar.CalendarProvider
import app.ieoseo.server.domain.calendar.ExternalEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

/**
 * ExternalEvent 영속화 (이슈 #59). 도메인 규칙은 service 계층에 둔다.
 *
 * 모든 조회는 소유자(userId)로 스코프한다(인증-도메인 §2). 동기화 upsert 는
 * [findByUserIdAndProviderAndExternalId] 로 기존 일정을 찾아 갱신/신규 분기한다.
 * 읽기 전용 외부 일정 범위 조회는 [findAllByUserIdAndDateBetween](ISO ymd LocalDate)을 쓴다.
 */
interface ExternalEventRepository : JpaRepository<ExternalEvent, UUID> {

    /** upsert 키 조회(소유자·provider·원본 id). */
    fun findByUserIdAndProviderAndExternalId(
        userId: UUID,
        provider: CalendarProvider,
        externalId: String,
    ): Optional<ExternalEvent>

    /** 소유자 스코프 날짜 범위(포함) 조회 — 캘린더 뷰 표시용. */
    fun findAllByUserIdAndDateBetween(userId: UUID, from: LocalDate, to: LocalDate): List<ExternalEvent>

    /** 연결 해제 시 해당 provider 의 외부 일정 정리(소유자 스코프). */
    fun deleteAllByUserIdAndProvider(userId: UUID, provider: CalendarProvider)
}
