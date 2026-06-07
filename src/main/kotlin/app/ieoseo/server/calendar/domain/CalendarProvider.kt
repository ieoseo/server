package app.ieoseo.server.calendar.domain

/**
 * 외부 캘린더 provider 3종 (FRD 4.5 / 4.12 / 5.7, 이슈 #59).
 *
 * - [GOOGLE] Google Calendar API(events.list)로 일정 조회.
 * - [NOTION] Notion API(데이터베이스 query)로 일정 조회.
 * - [APPLE]  Apple 은 공개 서버 API 가 없어 현재 미지원(스텁) — ADR-0010 참조.
 *
 * 인증용 [app.ieoseo.server.user.domain.AuthProvider](LOCAL/GOOGLE/APPLE/KAKAO)와는
 * 의미·값이 달라(여기는 NOTION 포함, KAKAO 없음) 별도 enum 으로 둔다.
 */
enum class CalendarProvider {
    GOOGLE,
    APPLE,
    NOTION,
}
