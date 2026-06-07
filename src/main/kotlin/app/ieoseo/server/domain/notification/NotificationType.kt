package app.ieoseo.server.domain.notification

/**
 * 인앱 알림 종류 (FRD 5.6 알림 규칙).
 *
 * - [DDAY]: D-Day N일 전(1/3/5/7) 마감 임박
 * - [DEBT_CREATED]: 미완료 → 시간부채("미룬 시간") 발생
 * - [DEBT_WARNING]: 부채 누적이 경고 임계를 넘음
 * - [STREAK]: 연속 달성(스트릭) 축하
 *
 * OS 푸시(FCM/APNs)는 범위 외(후속). 본 트랙은 인앱(벨 + 안 읽음)만 다룬다.
 */
enum class NotificationType {
    DDAY,
    DEBT_CREATED,
    DEBT_WARNING,
    STREAK,
}
