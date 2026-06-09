package app.ieoseo.server.user.domain

import app.ieoseo.server.global.common.BaseEntity
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
 * 사용자 Aggregate Root. id 는 Supabase JWT 의 sub(UUID)이며 앱이 주입한다(자동 생성 아님).
 * 자체 비밀번호/소셜 provider 개념 없음(Supabase Auth, ADR-0014).
 * email 은 nullable(이메일 미제공 provider 인 Kakao 등 지원, ADR-0017). nickname 은 필수.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uk_users_email", columnNames = ["email"])],
    indexes = [Index(name = "idx_users_email", columnList = "email")],
)
class User(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "email", nullable = true, length = 320)
    var email: String?,

    @Column(name = "nickname", nullable = false, length = 20)
    var nickname: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: UserStatus = UserStatus.ACTIVE,
) : BaseEntity() {

    init {
        // 이메일은 nullable(미제공 provider 지원, ADR-0017). null 이 아니면 비어 있을 수 없다.
        require(email?.isNotBlank() ?: true) { "email 은 null 이거나 비어 있지 않아야 한다" }
        require(nickname.isNotBlank()) { "nickname 은 비어 있을 수 없다" }
    }

    fun updateNickname(newNickname: String) {
        val trimmed = newNickname.trim()
        require(trimmed.isNotBlank()) { "nickname 은 비어 있을 수 없다" }
        require(trimmed.length <= NICKNAME_MAX_LENGTH) { "nickname 은 ${NICKNAME_MAX_LENGTH}자 이하여야 한다" }
        nickname = trimmed
    }

    fun withdraw() { status = UserStatus.WITHDRAWN }

    val isActive: Boolean get() = status == UserStatus.ACTIVE

    private companion object { const val NICKNAME_MAX_LENGTH = 20 }
}
