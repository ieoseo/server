package app.ieoseo.server.domain.calendar

/**
 * 캘린더 연결 상태 (FRD 4.12: 미연결 / 연결됨 / 인증 중 / 동기화 실패 / 권한 거부).
 *
 * 본 트랙(이슈 #59)은 토큰 등록 기반 수동 동기화이므로 다음 상태만 다룬다:
 * - [CONNECTED]   토큰 등록됨, 동기화 가능.
 * - [SYNC_FAILED] 마지막 동기화가 실패(토큰 만료·권한 거부·provider 오류). 재인증 필요.
 *
 * 미연결은 연결 레코드 부재로 표현한다(별도 enum 값 두지 않음).
 */
enum class ConnectionStatus {
    CONNECTED,
    SYNC_FAILED,
}
