package app.ieoseo.server.notification.domain

import java.util.UUID

/**
 * 인앱 알림 생성 규칙 (FRD 5.6) — 순수 로직.
 *
 * 입력(D-Day 남은 일수·부채 발생/누적·스트릭)만으로 [Notification] 후보를 산출한다.
 * 영속화/조회는 하지 않는다(service 책임). 임계 미달이면 null 을 반환해 "알림 없음"을 표현한다.
 * 날짜·시계에 의존하지 않으므로 결정적(deterministic)이며 단위 테스트가 쉽다.
 */
object NotificationRules {

    /** D-Day 알림 임계(일). 마감 N일 전 이 값들에만 알림한다. */
    private val DDAY_THRESHOLDS = setOf(1, 3, 5, 7)

    /** 부채 누적 경고 임계(분). 누적이 이 값을 초과하면 경고한다. */
    private const val DEBT_WARNING_MINUTES = 120

    /** 스트릭 축하 주기(일). 이 값의 배수에 도달하면 축하한다. */
    private const val STREAK_CELEBRATE_EVERY = 7

    /**
     * D-Day N일 전 알림. [daysRemaining] 이 임계(1/3/5/7) 가 아니면 null.
     * [refId] 는 대상 이벤트 id(선택).
     */
    fun dday(userId: UUID, refId: UUID?, title: String, daysRemaining: Int): Notification? {
        if (daysRemaining !in DDAY_THRESHOLDS) return null
        return Notification(
            userId = userId,
            type = NotificationType.DDAY,
            title = title,
            body = "‘$title’ 마감이 ${daysRemaining}일 남았어요",
            refId = refId,
        )
    }

    /** 시간부채 발생 알림(미완료 → 미룬 시간). 항상 생성한다. */
    fun debtCreated(userId: UUID, refId: UUID?, title: String, minutes: Int): Notification =
        Notification(
            userId = userId,
            type = NotificationType.DEBT_CREATED,
            title = "미룬 시간이 생겼어요",
            body = "‘$title’ 이(가) ${minutes}분짜리 미룬 시간으로 쌓였어요",
            refId = refId,
        )

    /** 부채 누적 경고. [totalMinutes] 가 경고 임계 이하면 null. */
    fun debtWarning(userId: UUID, totalMinutes: Int): Notification? {
        if (totalMinutes <= DEBT_WARNING_MINUTES) return null
        val hours = totalMinutes / 60
        return Notification(
            userId = userId,
            type = NotificationType.DEBT_WARNING,
            title = "미룬 시간이 쌓이고 있어요",
            body = "지금까지 미룬 시간이 ${hours}시간(${totalMinutes}분)을 넘었어요. 여유 있는 날로 옮겨볼까요?",
        )
    }

    /** 스트릭 축하. [streakDays] 가 0 이거나 축하 주기의 배수가 아니면 null. */
    fun streak(userId: UUID, streakDays: Int): Notification? {
        if (streakDays <= 0 || streakDays % STREAK_CELEBRATE_EVERY != 0) return null
        return Notification(
            userId = userId,
            type = NotificationType.STREAK,
            title = "스트릭 달성!",
            body = "${streakDays}일 연속 달성했어요. 흐름을 이어가요!",
        )
    }
}
