package app.ieoseo.server.settings.dto

import app.ieoseo.server.settings.service.UpdateUserSettingsCommand
import app.ieoseo.server.settings.domain.UserSettings
import app.ieoseo.server.settings.domain.WeekStart
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

/**
 * 사용자 설정 응답. 계약: `docs/05-API/auth.md`(GET/PUT /auth/me/settings).
 * 다크모드는 클라이언트 로컬 테마라 포함하지 않는다.
 */
data class SettingsResponse(
    val autoCarry: Boolean,
    val dayDeadlineHour: Int,
    val weekStart: WeekStart,
    val maxDailyMinutes: Int,
    val pomodoroFocus: Int,
    val pomodoroShortBreak: Int,
    val pomodoroLongBreak: Int,
    val completionSound: Boolean,
) {
    companion object {
        fun from(settings: UserSettings): SettingsResponse = SettingsResponse(
            autoCarry = settings.autoCarry,
            dayDeadlineHour = settings.dayDeadlineHour,
            weekStart = settings.weekStart,
            maxDailyMinutes = settings.maxDailyMinutes,
            pomodoroFocus = settings.pomodoroFocus,
            pomodoroShortBreak = settings.pomodoroShortBreak,
            pomodoroLongBreak = settings.pomodoroLongBreak,
            completionSound = settings.completionSound,
        )
    }
}

/**
 * 사용자 설정 수정 요청(PUT, 전체 교체). 경계 검증: 시각 0~23, 분 단위 양수·상한.
 * 도메인([UserSettings.update])도 동일 범위를 재방어한다.
 */
data class UpdateSettingsRequest(
    @field:NotNull val autoCarry: Boolean,
    @field:Min(0) @field:Max(23) val dayDeadlineHour: Int,
    @field:NotNull val weekStart: WeekStart,
    @field:Min(1) @field:Max(1440) val maxDailyMinutes: Int,
    @field:Min(1) @field:Max(180) val pomodoroFocus: Int,
    @field:Min(1) @field:Max(180) val pomodoroShortBreak: Int,
    @field:Min(1) @field:Max(180) val pomodoroLongBreak: Int,
    @field:NotNull val completionSound: Boolean,
) {
    fun toCommand(): UpdateUserSettingsCommand = UpdateUserSettingsCommand(
        autoCarry = autoCarry,
        dayDeadlineHour = dayDeadlineHour,
        weekStart = weekStart,
        maxDailyMinutes = maxDailyMinutes,
        pomodoroFocus = pomodoroFocus,
        pomodoroShortBreak = pomodoroShortBreak,
        pomodoroLongBreak = pomodoroLongBreak,
        completionSound = completionSound,
    )
}
