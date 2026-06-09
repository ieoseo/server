package app.ieoseo.server.settings.domain

import app.ieoseo.server.global.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * 사용자별 설정(UserSettings) — FRD 4.11 / 이슈 #56.
 *
 * User 와 1:1(소유자 [userId] 가 PK 겸 FK). 가입 시 기본값으로 생성하거나, 없으면 조회 시 lazy 기본값을
 * 만들어 반환한다(service). 다크모드는 클라이언트 로컬 테마로 유지하므로 여기 저장하지 않는다.
 *
 * 불변식: 마감 시각(시)은 0~23, 분 단위 값은 양수, 포모도로 길이는 양수.
 * audit(createdAt/updatedAt)는 [BaseEntity] 에서 상속한다.
 */
@Entity
@Table(name = "user_settings")
class UserSettings(
    /** 소유자(users.id). PK 겸 FK — 사용자당 1행. */
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    /** 자동 이월(미룬 시간) ON/OFF. 기본 ON. */
    @Column(name = "auto_carry", nullable = false)
    var autoCarry: Boolean = DEFAULT_AUTO_CARRY,

    /** 하루 마감 시각(시, 0~23). 기본 0(자정). */
    @Column(name = "day_deadline_hour", nullable = false)
    var dayDeadlineHour: Int = DEFAULT_DAY_DEADLINE_HOUR,

    /** 주간 시작 요일. 기본 MON. */
    @Enumerated(EnumType.STRING)
    @Column(name = "week_start", nullable = false, length = 8)
    var weekStart: WeekStart = WeekStart.MON,

    /** 하루 최대 예약 시간(분). 기본 480(8시간). */
    @Column(name = "max_daily_minutes", nullable = false)
    var maxDailyMinutes: Int = DEFAULT_MAX_DAILY_MINUTES,

    /** 포모도로 집중 길이(분). 기본 25. */
    @Column(name = "pomodoro_focus", nullable = false)
    var pomodoroFocus: Int = DEFAULT_POMODORO_FOCUS,

    /** 포모도로 짧은 휴식(분). 기본 5. */
    @Column(name = "pomodoro_short_break", nullable = false)
    var pomodoroShortBreak: Int = DEFAULT_POMODORO_SHORT_BREAK,

    /** 포모도로 긴 휴식(분). 기본 15. */
    @Column(name = "pomodoro_long_break", nullable = false)
    var pomodoroLongBreak: Int = DEFAULT_POMODORO_LONG_BREAK,

    /** 완료음 ON/OFF. 기본 ON. */
    @Column(name = "completion_sound", nullable = false)
    var completionSound: Boolean = DEFAULT_COMPLETION_SOUND,
) : BaseEntity() {

    init {
        validate(dayDeadlineHour, maxDailyMinutes, pomodoroFocus, pomodoroShortBreak, pomodoroLongBreak)
    }

    /**
     * 설정 전체 수정(PUT, 이슈 #56). 값은 호출 경계(DTO)에서 1차 검증하고 도메인에서도 범위를 방어한다.
     * 기존 객체를 갱신한다(JPA dirty checking).
     */
    fun update(
        autoCarry: Boolean,
        dayDeadlineHour: Int,
        weekStart: WeekStart,
        maxDailyMinutes: Int,
        pomodoroFocus: Int,
        pomodoroShortBreak: Int,
        pomodoroLongBreak: Int,
        completionSound: Boolean,
    ) {
        validate(dayDeadlineHour, maxDailyMinutes, pomodoroFocus, pomodoroShortBreak, pomodoroLongBreak)
        this.autoCarry = autoCarry
        this.dayDeadlineHour = dayDeadlineHour
        this.weekStart = weekStart
        this.maxDailyMinutes = maxDailyMinutes
        this.pomodoroFocus = pomodoroFocus
        this.pomodoroShortBreak = pomodoroShortBreak
        this.pomodoroLongBreak = pomodoroLongBreak
        this.completionSound = completionSound
    }

    private fun validate(
        dayDeadlineHour: Int,
        maxDailyMinutes: Int,
        pomodoroFocus: Int,
        pomodoroShortBreak: Int,
        pomodoroLongBreak: Int,
    ) {
        require(dayDeadlineHour in 0..23) { "dayDeadlineHour 는 0~23 이어야 한다" }
        require(maxDailyMinutes in 1..MAX_DAILY_MINUTES_LIMIT) {
            "maxDailyMinutes 는 1~$MAX_DAILY_MINUTES_LIMIT 이어야 한다"
        }
        require(pomodoroFocus in 1..POMODORO_LIMIT) { "pomodoroFocus 는 1~$POMODORO_LIMIT 이어야 한다" }
        require(pomodoroShortBreak in 1..POMODORO_LIMIT) { "pomodoroShortBreak 는 1~$POMODORO_LIMIT 이어야 한다" }
        require(pomodoroLongBreak in 1..POMODORO_LIMIT) { "pomodoroLongBreak 는 1~$POMODORO_LIMIT 이어야 한다" }
    }

    companion object {
        const val DEFAULT_AUTO_CARRY = true
        const val DEFAULT_DAY_DEADLINE_HOUR = 0
        const val DEFAULT_MAX_DAILY_MINUTES = 480
        const val DEFAULT_POMODORO_FOCUS = 25
        const val DEFAULT_POMODORO_SHORT_BREAK = 5
        const val DEFAULT_POMODORO_LONG_BREAK = 15
        const val DEFAULT_COMPLETION_SOUND = true

        const val MAX_DAILY_MINUTES_LIMIT = 1440
        const val POMODORO_LIMIT = 180

        /** 사용자 기본 설정(가입 시 또는 조회 시 lazy 생성). */
        fun defaults(userId: UUID): UserSettings = UserSettings(userId = userId)
    }
}
