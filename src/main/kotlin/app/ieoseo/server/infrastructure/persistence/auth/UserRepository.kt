package app.ieoseo.server.infrastructure.persistence.auth

import app.ieoseo.server.domain.auth.AuthProvider
import app.ieoseo.server.domain.auth.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * User Aggregate 영속화. 인증 규칙은 service 계층(AuthService/TokenService)에 둔다.
 */
interface UserRepository : JpaRepository<User, UUID> {

    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?
}
