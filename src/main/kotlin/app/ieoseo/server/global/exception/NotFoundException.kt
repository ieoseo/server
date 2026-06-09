package app.ieoseo.server.global.exception

/**
 * 리소스를 찾지 못했을 때 던진다. GlobalExceptionHandler 가 404 + envelope 오류로 매핑한다.
 */
class NotFoundException(
    val resource: String,
    val id: Any,
) : RuntimeException("$resource(id=$id) 를 찾을 수 없습니다")
