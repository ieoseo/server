package app.ieoseo.server.application.auth

/**
 * 인증 도메인 예외. GlobalExceptionHandler 가 `docs/05-API/auth.md` 오류 코드로 매핑한다.
 */

/** 이미 가입된 이메일로 회원가입을 시도. → 409 EMAIL_TAKEN. */
class EmailTakenException(email: String) : RuntimeException("이미 가입된 이메일입니다: $email")

/**
 * 로그인 실패(이메일 없음 또는 비밀번호 불일치). → 401 INVALID_CREDENTIALS.
 * 정보 노출 최소화를 위해 이메일/비밀번호 원인을 구분하지 않는다.
 */
class InvalidCredentialsException : RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다")

/**
 * 소셜 로그인 이메일이 이미 LOCAL 계정으로 존재. → 409 EMAIL_LINKED_LOCAL.
 * 정책(인증-도메인 §1): 자동 연결 거부 + 안내(추후 계정 연동).
 */
class EmailLinkedLocalException(email: String) :
    RuntimeException("이미 이메일/비밀번호로 가입된 계정입니다: $email")
