package app.ieoseo.server.calendar.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Google Calendar OAuth(서버 주도) 설정(이슈 #9). 시크릿 하드코딩 금지 — 값은 환경변수로 주입.
 *
 * 흐름: 앱이 [authUri] 동의 URL 로 이동 → 사용자 동의 → Google 이 [redirectUri](서버 콜백)로
 * code 전달 → 서버가 [tokenUri] 에서 access/refresh 토큰 교환 → 저장 → [returnDeeplink] 로 앱 복귀.
 *
 * - [clientId]/[clientSecret]: Google Cloud OAuth 2.0 Web 클라이언트(`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`).
 * - [redirectUri]: 서버 콜백(`GOOGLE_CALENDAR_REDIRECT_URI`). Google Cloud 콘솔 승인된 redirect URI 와 일치해야 함.
 * - [returnDeeplink]: 콜백 처리 후 앱으로 복귀할 딥링크(`GOOGLE_CALENDAR_RETURN_DEEPLINK`, 예: app.ieoseo://calendar-callback).
 * - [scope]: 요청 스코프(기본 calendar.readonly).
 * - [authUri]/[tokenUri]: Google 엔드포인트(기본값 고정, 테스트에서 가짜 서버로 덮어쓰기 가능).
 *
 * 미설정(빈 clientId) 이면 연동 비활성 — 서비스가 명확한 오류와 로그로 빠르게 실패한다.
 */
@ConfigurationProperties(prefix = "ieoseo.google")
data class GoogleOAuthProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    val returnDeeplink: String = "",
    val scope: String = "https://www.googleapis.com/auth/calendar.readonly",
    val authUri: String = "https://accounts.google.com/o/oauth2/v2/auth",
    val tokenUri: String = "https://oauth2.googleapis.com/token",
) {
    /** 연동에 필요한 필수 값이 모두 채워졌는지(미설정 시 연동 비활성). */
    val isConfigured: Boolean
        get() = clientId.isNotBlank() &&
            clientSecret.isNotBlank() &&
            redirectUri.isNotBlank() &&
            returnDeeplink.isNotBlank()
}
