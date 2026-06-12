package app.ieoseo.server.global.exception

import app.ieoseo.server.global.common.ApiError
import app.ieoseo.server.global.common.ApiResponse
import app.ieoseo.server.global.common.FieldError
import app.ieoseo.server.global.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 예외를 공통 envelope 오류로 매핑한다. 계약: `docs/05-API/README.md` 오류 형식.
 *
 * 미처리(5xx) 예외는 ERROR 로그로만 남긴다(외부 관측성 미사용).
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiResponse.fail(ApiError(code = "NOT_FOUND", message = ex.message ?: "리소스를 찾을 수 없습니다")),
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val details = ex.bindingResult.fieldErrors.map {
            FieldError(field = it.field, message = it.defaultMessage ?: "유효하지 않은 값입니다")
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse.fail(
                ApiError(code = "VALIDATION_ERROR", message = "입력값 검증에 실패했습니다", details = details),
            ),
        )
    }

    /** 본문 파싱 실패(필수 필드 누락·타입 불일치 등) → 400 BAD_REQUEST. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse.fail(ApiError(code = "BAD_REQUEST", message = "요청 본문을 읽을 수 없습니다")),
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("잘못된 요청(400): {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse.fail(ApiError(code = "BAD_REQUEST", message = ex.message ?: "잘못된 요청입니다")),
        )
    }

    /** 상태 전이 충돌 등 도메인 상태 위반(예: 불법 상태 전이) → 409 CONFLICT. */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("상태 충돌(409): {}", ex.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiResponse.fail(ApiError(code = "CONFLICT", message = ex.message ?: "현재 상태에서 처리할 수 없습니다")),
        )
    }

    /**
     * 위에서 매핑되지 않은 런타임 예외(NPE·DB 오류 등) → 500 INTERNAL_ERROR.
     *
     * 4xx 도메인 예외는 각자 전용 핸들러가 처리하므로 여기 도달하지 않는다. 즉 이 핸들러는
     * "진짜 예상 못한" 서버 오류만 받아 ERROR 로그로 남긴다. 사용자에게는 내부 상세를
     * 노출하지 않는 일반 메시지만 돌려준다.
     */
    @ExceptionHandler(RuntimeException::class)
    fun handleUnexpected(ex: RuntimeException): ResponseEntity<ApiResponse<Nothing>> {
        log.error("처리되지 않은 예외", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponse.fail(ApiError(code = "INTERNAL_ERROR", message = "서버 오류가 발생했습니다")),
        )
    }
}
