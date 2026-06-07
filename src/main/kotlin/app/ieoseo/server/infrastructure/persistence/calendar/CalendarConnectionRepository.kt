package app.ieoseo.server.infrastructure.persistence.calendar

import app.ieoseo.server.domain.calendar.CalendarConnection
import app.ieoseo.server.domain.calendar.CalendarProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

/**
 * CalendarConnection 영속화 (이슈 #59). 도메인 규칙은 service 계층에 둔다.
 *
 * 모든 조회는 소유자(userId)로 스코프한다(인증-도메인 §2). 사용자·provider 당 1개를 보장하는
 * `(user_id, provider)` 유니크는 application 계층(connect 시 조회 후 갱신)으로 강제한다.
 */
interface CalendarConnectionRepository : JpaRepository<CalendarConnection, UUID> {

    /** 소유자 스코프 전체 연결(설정 화면 목록). */
    fun findAllByUserId(userId: UUID): List<CalendarConnection>

    /** 소유자·provider 단건(connect upsert·disconnect·sync 에서 사용). */
    fun findByUserIdAndProvider(userId: UUID, provider: CalendarProvider): Optional<CalendarConnection>
}
