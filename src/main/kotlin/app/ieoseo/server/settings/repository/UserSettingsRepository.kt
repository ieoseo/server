package app.ieoseo.server.settings.repository

import app.ieoseo.server.settings.domain.UserSettings
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * UserSettings 영속화 (#56). 사용자당 1행(PK = userId). 조회 정책은 service 가 둔다(없으면 기본값).
 */
interface UserSettingsRepository : JpaRepository<UserSettings, UUID>
