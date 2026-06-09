package app.ieoseo.server.task.controller

import app.ieoseo.server.task.dto.*

import app.ieoseo.server.task.domain.RecurrenceFrequency
import app.ieoseo.server.task.domain.RecurrenceRule
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 태스크 반복 규칙 DTO 매핑 단위 테스트 (FRD 5.4).
 *
 * - 생성 요청 DTO 의 [RecurrenceDto] → 도메인 [RecurrenceRule] 변환.
 * - 응답 DTO 가 도메인 규칙을 계약 형태(frequency·weeklyDays 코드·monthDay·yearMonth/Day)로 노출.
 * - recurrence 미지정(null) 은 NONE 으로 매핑(비파괴 기본값).
 */
class TaskRecurrenceDtoTest {

    @Test
    fun `recurrence 미지정 생성요청은 NONE 규칙으로 매핑된다`() {
        val request = TaskCreateRequest(title = "단발", estimatedMinutes = 30, date = LocalDate.of(2026, 6, 4))

        val task = request.toEntity(UUID.randomUUID())

        assertEquals(RecurrenceFrequency.NONE, task.recurrence.frequency)
    }

    @Test
    fun `WEEKLY recurrence 생성요청은 요일 집합 규칙으로 매핑된다`() {
        val request = TaskCreateRequest(
            title = "영어 단어",
            estimatedMinutes = 30,
            date = LocalDate.of(2026, 6, 1),
            recurrence = RecurrenceDto(frequency = RecurrenceFrequency.WEEKLY, weeklyDays = setOf("MON", "WED", "FRI")),
        )

        val task = request.toEntity(UUID.randomUUID())

        assertEquals(RecurrenceFrequency.WEEKLY, task.recurrence.frequency)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            task.recurrence.weeklyDays,
        )
    }

    @Test
    fun `MONTHLY recurrence 생성요청은 일자 규칙으로 매핑된다`() {
        val request = TaskCreateRequest(
            title = "월세",
            estimatedMinutes = 15,
            date = LocalDate.of(2026, 6, 15),
            recurrence = RecurrenceDto(frequency = RecurrenceFrequency.MONTHLY, monthDay = 15),
        )

        val task = request.toEntity(UUID.randomUUID())

        assertEquals(RecurrenceFrequency.MONTHLY, task.recurrence.frequency)
        assertEquals(15, task.recurrence.monthDay)
    }

    @Test
    fun `YEARLY recurrence 생성요청은 월일 규칙으로 매핑된다`() {
        val request = TaskCreateRequest(
            title = "건강검진",
            estimatedMinutes = 120,
            date = LocalDate.of(2026, 8, 2),
            recurrence = RecurrenceDto(frequency = RecurrenceFrequency.YEARLY, yearMonth = 8, yearDay = 2),
        )

        val task = request.toEntity(UUID.randomUUID())

        assertEquals(RecurrenceFrequency.YEARLY, task.recurrence.frequency)
        assertEquals(8, task.recurrence.yearMonth)
        assertEquals(2, task.recurrence.yearDay)
    }

    @Test
    fun `응답 DTO 는 WEEKLY 규칙을 요일 코드 집합으로 노출한다`() {
        val rule = RecurrenceRule.weekly(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))

        val dto = RecurrenceDto.from(rule)

        assertEquals(RecurrenceFrequency.WEEKLY, dto.frequency)
        assertEquals(setOf("TUE", "THU"), dto.weeklyDays)
        assertNull(dto.monthDay)
    }

    @Test
    fun `응답 DTO 는 NONE 규칙을 빈 요일과 null 필드로 노출한다`() {
        val dto = RecurrenceDto.from(RecurrenceRule.none())

        assertEquals(RecurrenceFrequency.NONE, dto.frequency)
        assertTrue(dto.weeklyDays.isEmpty())
        assertNull(dto.monthDay)
        assertNull(dto.yearMonth)
        assertNull(dto.yearDay)
    }

    @Test
    fun `TaskResponse 는 도메인 recurrence 를 RecurrenceDto 로 포함한다`() {
        val task = app.ieoseo.server.task.domain.Task(
            userId = UUID.randomUUID(),
            title = "영어 단어",
            estimatedMinutes = 30,
            date = LocalDate.of(2026, 6, 1),
            recurrence = RecurrenceRule.monthly(10),
        )

        val response = TaskResponse.from(task)

        assertEquals(RecurrenceFrequency.MONTHLY, response.recurrence.frequency)
        assertEquals(10, response.recurrence.monthDay)
    }
}
