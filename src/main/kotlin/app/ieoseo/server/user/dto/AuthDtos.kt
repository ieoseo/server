package app.ieoseo.server.user.dto

import app.ieoseo.server.user.domain.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class UpdateProfileRequest(
    @field:NotBlank @field:Size(min = 1, max = 20) val nickname: String,
)

data class UserResponse(
    val id: UUID,
    val email: String?,
    val nickname: String,
    /** 이번 요청에 막 provisioning 된 신규 사용자인지(client 닉네임 설정 화면 트리거용). */
    val isNew: Boolean = false,
) {
    companion object {
        fun from(user: User, isNew: Boolean = false): UserResponse =
            UserResponse(id = user.id, email = user.email, nickname = user.nickname, isNew = isNew)
    }
}
