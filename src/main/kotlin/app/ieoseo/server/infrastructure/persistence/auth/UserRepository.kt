package app.ieoseo.server.infrastructure.persistence.auth

import app.ieoseo.server.domain.auth.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID>
