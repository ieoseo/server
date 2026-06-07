package app.ieoseo.server.settings.service

import app.ieoseo.server.settings.domain.UserSettings
import app.ieoseo.server.settings.domain.WeekStart
import app.ieoseo.server.settings.repository.UserSettingsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 사용자 설정 유스케이스 (#56). 계약: `docs/05-API/auth.md`(GET/PUT /auth/me/settings).
 *
 * - [get]: userId 로 설정 조회. 없으면 기본값을 만들어 저장 후 반환(lazy 기본, 멱등).
 * - [update]: 전체 수정(PUT). 없으면 기본값에서 생성 후 갱신. 도메인이 범위를 방어한다.
 *
 * 다크모드는 클라이언트 로컬 테마로 유지하므로 여기서 다루지 않는다.
 */
@Service
@Transactional(readOnly = true)
class UserSettingsService(
    private val repository: UserSettingsRepository,
) {
    /** 설정 조회(없으면 기본값 lazy 생성·저장). */
    @Transactional
    fun get(userId: UUID): UserSettings =
        repository.findById(userId).orElseGet { repository.save(UserSettings.defaults(userId)) }

    /** 설정 전체 수정. 없으면 기본값에서 만든 뒤 갱신해 저장한다. */
    @Transactional
    fun update(userId: UUID, command: UpdateUserSettingsCommand): UserSettings {
        val settings = repository.findById(userId).orElseGet { UserSettings.defaults(userId) }
        settings.update(
            autoCarry = command.autoCarry,
            dayDeadlineHour = command.dayDeadlineHour,
            weekStart = command.weekStart,
            maxDailyMinutes = command.maxDailyMinutes,
            pomodoroFocus = command.pomodoroFocus,
            pomodoroShortBreak = command.pomodoroShortBreak,
            pomodoroLongBreak = command.pomodoroLongBreak,
            completionSound = command.completionSound,
        )
        return repository.save(settings)
    }
}

/**
 * 설정 수정 명령(application 경계). presentation DTO 가 검증 후 변환한다.
 */
data class UpdateUserSettingsCommand(
    val autoCarry: Boolean,
    val dayDeadlineHour: Int,
    val weekStart: WeekStart,
    val maxDailyMinutes: Int,
    val pomodoroFocus: Int,
    val pomodoroShortBreak: Int,
    val pomodoroLongBreak: Int,
    val completionSound: Boolean,
)
