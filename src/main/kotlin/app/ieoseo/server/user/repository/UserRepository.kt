package app.ieoseo.server.user.repository

import app.ieoseo.server.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID>
