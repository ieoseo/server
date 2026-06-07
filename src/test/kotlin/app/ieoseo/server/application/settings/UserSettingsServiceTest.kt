package app.ieoseo.server.application.settings

import app.ieoseo.server.domain.settings.UserSettings
import app.ieoseo.server.domain.settings.WeekStart
import app.ieoseo.server.infrastructure.persistence.settings.UserSettingsRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UserSettingsService 단위 테스트 (#56) — repository mock.
 *
 * - get: 없으면 기본값 lazy 생성·저장 후 반환.
 * - update: 전체 교체, 없으면 기본값에서 생성 후 갱신.
 */
class UserSettingsServiceTest {

    private val repository: UserSettingsRepository = mock(UserSettingsRepository::class.java)
    private val service = UserSettingsService(repository)
    private val userId = UUID.randomUUID()

    private fun command() = UpdateUserSettingsCommand(
        autoCarry = false,
        dayDeadlineHour = 3,
        weekStart = WeekStart.SUN,
        maxDailyMinutes = 600,
        pomodoroFocus = 50,
        pomodoroShortBreak = 10,
        pomodoroLongBreak = 30,
        completionSound = false,
    )

    @Test
    fun `get 은 설정이 없으면 기본값을 만들어 저장하고 반환한다`() {
        `when`(repository.findById(userId)).thenReturn(Optional.empty())
        `when`(repository.save(any(UserSettings::class.java))).thenAnswer { it.arguments[0] }

        val settings = service.get(userId)

        assertEquals(userId, settings.userId)
        assertTrue(settings.autoCarry)
        assertEquals(WeekStart.MON, settings.weekStart)
        assertEquals(UserSettings.DEFAULT_MAX_DAILY_MINUTES, settings.maxDailyMinutes)
    }

    @Test
    fun `get 은 기존 설정이 있으면 그대로 반환한다`() {
        val existing = UserSettings(userId = userId, autoCarry = false, weekStart = WeekStart.SUN)
        `when`(repository.findById(userId)).thenReturn(Optional.of(existing))

        val settings = service.get(userId)

        assertEquals(false, settings.autoCarry)
        assertEquals(WeekStart.SUN, settings.weekStart)
    }

    @Test
    fun `update 는 없으면 기본값에서 만든 뒤 전체 교체한다`() {
        `when`(repository.findById(userId)).thenReturn(Optional.empty())
        `when`(repository.save(any(UserSettings::class.java))).thenAnswer { it.arguments[0] }

        val settings = service.update(userId, command())

        assertEquals(false, settings.autoCarry)
        assertEquals(3, settings.dayDeadlineHour)
        assertEquals(WeekStart.SUN, settings.weekStart)
        assertEquals(600, settings.maxDailyMinutes)
        assertEquals(50, settings.pomodoroFocus)
        assertEquals(false, settings.completionSound)
    }

    @Test
    fun `update 는 기존 설정을 갱신한다`() {
        val existing = UserSettings(userId = userId)
        `when`(repository.findById(userId)).thenReturn(Optional.of(existing))
        `when`(repository.save(any(UserSettings::class.java))).thenAnswer { it.arguments[0] }

        val settings = service.update(userId, command())

        assertEquals(userId, settings.userId)
        assertEquals(WeekStart.SUN, settings.weekStart)
        assertEquals(10, settings.pomodoroShortBreak)
    }
}
