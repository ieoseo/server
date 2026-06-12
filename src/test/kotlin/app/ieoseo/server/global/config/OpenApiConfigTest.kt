package app.ieoseo.server.global.config

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [OpenApiConfig] 단위 테스트. OpenAPI 빈이 이어서 API 메타데이터를 노출하는지 검증한다.
 */
class OpenApiConfigTest {

    @Test
    fun `OpenAPI 빈은 이어서 API 메타데이터를 노출한다`() {
        val openApi = OpenApiConfig().ieoseoOpenApi()

        assertEquals("이어서 API", openApi.info.title)
        assertEquals("v1", openApi.info.version)
    }
}
