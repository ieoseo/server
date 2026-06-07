package app.ieoseo.server.domain.calendar

import app.ieoseo.server.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 외부 캘린더 연결 Aggregate Root (FRD 4.12 / 5.7, 이슈 #59).
 *
 * 사용자별·provider별 1개의 연결을 둔다(`(user_id, provider)` 유니크). 연결은 토큰 등록으로
 * 생성되고, 수동 동기화 시 [lastSyncedAt]/[status] 가 갱신된다.
 *
 * 토큰 민감도: [accessToken]/[refreshToken] 은 민감 정보다. 본 트랙은 DB 컬럼에 평문 저장하되,
 * **운영에서는 컬럼 암호화(또는 KMS/secret 저장소)를 적용할 것을 권장**한다(ADR-0010 제약).
 * 응답 DTO 에는 절대 토큰을 싣지 않는다(presentation 계층에서 제외).
 *
 * id 전략: 애플리케이션 생성 UUID(다른 Aggregate 와 동일, event/task 참고).
 */
@Entity
@Table(
    name = "calendar_connections",
    indexes = [
        Index(name = "idx_calendar_connections_user_id", columnList = "user_id"),
    ],
)
class CalendarConnection(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    /** 소유자(users.id). 모든 조회/쓰기는 이 값으로 스코프된다(인증-도메인 §2). */
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16, updatable = false)
    val provider: CalendarProvider,

    /** provider access token(민감). 운영 암호화 권장. null 이면 미발급/스텁. */
    @Column(name = "access_token", length = 2048)
    var accessToken: String? = null,

    /** provider refresh token(민감). 운영 암호화 권장. */
    @Column(name = "refresh_token", length = 2048)
    var refreshToken: String? = null,

    /** access token 만료 시각(있으면 갱신 판단에 사용). */
    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: ConnectionStatus = ConnectionStatus.CONNECTED,

    /** 마지막 성공 동기화 시각. 한 번도 동기화하지 않았으면 null. */
    @Column(name = "last_synced_at")
    var lastSyncedAt: Instant? = null,
) : BaseEntity() {

    /** 동기화 성공 기록(상태 정상화 + 시각 갱신). */
    fun markSynced(at: Instant = Instant.now()) {
        status = ConnectionStatus.CONNECTED
        lastSyncedAt = at
    }

    /** 동기화 실패 기록(재인증 유도). */
    fun markSyncFailed() {
        status = ConnectionStatus.SYNC_FAILED
    }
}
