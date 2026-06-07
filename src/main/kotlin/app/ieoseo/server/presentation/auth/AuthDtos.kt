package app.ieoseo.server.presentation.auth

import app.ieoseo.server.domain.auth.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class UpdateProfileRequest(
    @field:NotBlank @field:Size(min = 1, max = 20) val nickname: String,
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val nickname: String,
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(id = user.id, email = user.email, nickname = user.nickname)
    }
}
