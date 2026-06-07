package app.ieoseo.server.domain.auth

import app.ieoseo.server.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

/**
 * 사용자 Aggregate Root. 계약: `docs/06-백엔드/인증-도메인.md` §1.
 *
 * 불변식:
 * - [provider] == LOCAL 이면 [passwordHash] != null (이메일/비밀번호 계정).
 * - [provider] != LOCAL 이면 [providerId] != null (소셜 계정, `(provider, providerId)` 유니크).
 *
 * audit(createdAt/updatedAt)는 [BaseEntity] 에서 상속한다.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_users_email", columnNames = ["email"]),
        UniqueConstraint(name = "uk_users_provider_provider_id", columnNames = ["provider", "provider_id"]),
    ],
    indexes = [
        Index(name = "idx_users_email", columnList = "email"),
    ],
)
class User(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "email", nullable = false, length = 320)
    var email: String,

    @Column(name = "nickname", nullable = false, length = 20)
    var nickname: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    var provider: AuthProvider = AuthProvider.LOCAL,

    /** BCrypt 해시. LOCAL 계정은 필수, 소셜 전용 계정은 null. 평문 로깅 금지. */
    @Column(name = "password_hash", length = 100)
    var passwordHash: String? = null,

    /** 소셜 provider 발급 고유 id. 소셜 계정은 필수. */
    @Column(name = "provider_id", length = 255)
    var providerId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: UserStatus = UserStatus.ACTIVE,
) : BaseEntity() {

    init {
        require(email.isNotBlank()) { "email 은 비어 있을 수 없다" }
        require(nickname.isNotBlank()) { "nickname 은 비어 있을 수 없다" }
        if (provider == AuthProvider.LOCAL) {
            require(passwordHash != null) { "LOCAL 사용자는 passwordHash 가 필요하다" }
        } else {
            require(providerId != null) { "소셜 사용자는 providerId 가 필요하다" }
        }
    }

    /**
     * 닉네임을 변경한다(프로필 수정, 이슈 #56). 공백/길이는 호출 경계(DTO)에서 1차 검증하고,
     * 도메인에서도 빈 값과 길이 상한을 방어한다(trim 후 저장).
     */
    fun updateNickname(newNickname: String) {
        val trimmed = newNickname.trim()
        require(trimmed.isNotBlank()) { "nickname 은 비어 있을 수 없다" }
        require(trimmed.length <= NICKNAME_MAX_LENGTH) { "nickname 은 ${NICKNAME_MAX_LENGTH}자 이하여야 한다" }
        nickname = trimmed
    }

    /**
     * 회원 탈퇴(소프트 삭제, 이슈 #56). [status] 를 [UserStatus.WITHDRAWN] 으로 전이한다.
     * 이미 탈퇴한 계정의 재탈퇴는 멱등(no-op)으로 둔다. refresh 토큰 폐기는 service 가 수행한다.
     */
    fun withdraw() {
        status = UserStatus.WITHDRAWN
    }

    /** 활성 계정인지(탈퇴 전). */
    val isActive: Boolean get() = status == UserStatus.ACTIVE

    private companion object {
        const val NICKNAME_MAX_LENGTH = 20
    }
}
