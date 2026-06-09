package app.ieoseo.server.settings.domain

/**
 * 주간 시작 요일(사용자 설정, FRD 4.11 / 이슈 #56).
 * - [MON]: 월요일 시작(기본).
 * - [SUN]: 일요일 시작.
 */
enum class WeekStart {
    MON,
    SUN,
}
