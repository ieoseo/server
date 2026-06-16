package app.ieoseo.server.calendar.client

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 외부 일정 표시 타임존. 외부 캘린더(Google/Notion)는 일정 시각을 임의 오프셋(UTC 등)으로
 * 내려주는데, 그 절대 시각을 그대로 쓰면 KST 08:00 이 UTC 23:00(전날)로 표시되는 어긋남이 생긴다.
 * 절대 시각을 이 타임존으로 환산해 (날짜, "HH:mm") 을 뽑는다.
 *
 * 현재 한국어 전용 서비스라 [DISPLAY_ZONE] 은 Asia/Seoul 고정. 향후 사용자 타임존 설정이
 * 생기면 그 값으로 대체한다(단일 지점이라 교체가 쉽다).
 */
val DISPLAY_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/** 절대 시각([OffsetDateTime])을 표시 타임존 기준 (날짜, `HH:mm`) 으로 변환한다. */
fun OffsetDateTime.toDisplayDateAndTime(): Pair<LocalDate, String> {
    val zoned = atZoneSameInstant(DISPLAY_ZONE)
    return zoned.toLocalDate() to "%02d:%02d".format(zoned.hour, zoned.minute)
}
