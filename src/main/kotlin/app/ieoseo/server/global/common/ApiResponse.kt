package app.ieoseo.server.global.common

/**
 * 모든 REST 응답의 공통 envelope.
 *
 * 계약: `docs/05-API/README.md` ({ success, data, error, meta }).
 * 성공 시 [error] 는 null, 실패 시 [data] 는 null 이다.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
    val meta: Meta? = null,
) {
    companion object {
        fun <T> ok(data: T, meta: Meta? = null): ApiResponse<T> =
            ApiResponse(success = true, data = data, meta = meta)

        fun <T> fail(error: ApiError): ApiResponse<T> =
            ApiResponse(success = false, error = error)
    }
}

/**
 * 오류 본문. [code] 는 기계 판독용 안정 식별자, [message] 는 사용자 친화 메시지.
 * [details] 는 필드 단위 검증 오류 등 부가 정보(선택).
 */
data class ApiError(
    val code: String,
    val message: String,
    val details: List<FieldError>? = null,
)

data class FieldError(
    val field: String,
    val message: String,
)

/**
 * 페이지네이션 메타. 목록 응답에서만 채운다.
 */
data class Meta(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
