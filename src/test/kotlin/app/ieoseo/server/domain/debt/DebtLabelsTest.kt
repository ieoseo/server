package app.ieoseo.server.domain.debt

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * 시간부채 출처 라벨([DebtLabels]) 단위 테스트 (FRD 5.3).
 *
 * originDate 의 한국어 요일 라벨을 today 기준 이번 주/지난주로 구분한다.
 */
class DebtLabelsTest {

    // 기준 오늘: 2026-06-03(수요일). 이번 주 월요일 = 2026-06-01.
    private val today = LocalDate.of(2026, 6, 3)

    @Test
    fun `이번 주 발생일은 요일만 표시한다`() {
        assertEquals("월요일", DebtLabels.fromLabel(LocalDate.of(2026, 6, 1), today))
        assertEquals("화요일", DebtLabels.fromLabel(LocalDate.of(2026, 6, 2), today))
    }

    @Test
    fun `지난주 발생일은 지난주 접두사를 붙인다`() {
        // 2026-05-29 는 지난주 금요일.
        assertEquals("지난주 금요일", DebtLabels.fromLabel(LocalDate.of(2026, 5, 29), today))
        // 2026-05-27 은 지난주 수요일.
        assertEquals("지난주 수요일", DebtLabels.fromLabel(LocalDate.of(2026, 5, 27), today))
    }

    @Test
    fun `그보다 더 오래된 날은 월일 라벨로 표시한다`() {
        // 2주 이상 전: 요일 라벨이 헷갈리므로 날짜로.
        assertEquals("5월 18일", DebtLabels.fromLabel(LocalDate.of(2026, 5, 18), today))
    }
}
