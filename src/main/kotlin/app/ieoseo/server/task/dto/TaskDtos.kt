package app.ieoseo.server.task.dto

import app.ieoseo.server.task.domain.RecurrenceFrequency
import app.ieoseo.server.task.domain.RecurrenceRule
import app.ieoseo.server.task.domain.Task
import app.ieoseo.server.task.domain.TaskState
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 반복 규칙 DTO (FRD 5.4). 도메인 [RecurrenceRule] 의 계약 표현.
 *
 * - [frequency]: NONE/WEEKLY/MONTHLY/YEARLY.
 * - [weeklyDays]: WEEKLY 요일 코드 집합(`MON`/`TUE`/`WED`/`THU`/`FRI`/`SAT`/`SUN`).
 * - [monthDay]: MONTHLY 일자(1~31).
 * - [yearMonth]/[yearDay]: YEARLY 월(1~12)/일(1~31).
 *
 * 도메인으로의 변환([toDomain])은 값객체 생성을 거치므로 잘못된 조합(빈 요일·없는 일자 등)은
 * `IllegalArgumentException` 으로 거부되어 경계에서 400 VALIDATION_ERROR 로 매핑된다.
 */
data class RecurrenceDto(
    @field:NotNull val frequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val weeklyDays: Set<String> = emptySet(),
    val monthDay: Int? = null,
    val yearMonth: Int? = null,
    val yearDay: Int? = null,
) {
    fun toDomain(): RecurrenceRule = when (frequency) {
        RecurrenceFrequency.NONE -> RecurrenceRule.none()
        RecurrenceFrequency.WEEKLY -> RecurrenceRule.weekly(weeklyDays.map(::dayOfWeekFromCode).toSet())
        RecurrenceFrequency.MONTHLY ->
            RecurrenceRule.monthly(requireNotNull(monthDay) { "MONTHLY 반복은 monthDay 가 필요하다" })
        RecurrenceFrequency.YEARLY -> RecurrenceRule.yearly(
            requireNotNull(yearMonth) { "YEARLY 반복은 yearMonth 가 필요하다" },
            requireNotNull(yearDay) { "YEARLY 반복은 yearDay 가 필요하다" },
        )
    }

    companion object {
        fun from(rule: RecurrenceRule): RecurrenceDto = RecurrenceDto(
            frequency = rule.frequency,
            weeklyDays = rule.weeklyDays.map(::dayOfWeekToCode).toSet(),
            monthDay = rule.monthDay,
            yearMonth = rule.yearMonth,
            yearDay = rule.yearDay,
        )
    }
}

private val WEEKDAY_CODES: Map<DayOfWeek, String> = mapOf(
    DayOfWeek.MONDAY to "MON",
    DayOfWeek.TUESDAY to "TUE",
    DayOfWeek.WEDNESDAY to "WED",
    DayOfWeek.THURSDAY to "THU",
    DayOfWeek.FRIDAY to "FRI",
    DayOfWeek.SATURDAY to "SAT",
    DayOfWeek.SUNDAY to "SUN",
)

private fun dayOfWeekToCode(dow: DayOfWeek): String = WEEKDAY_CODES.getValue(dow)

private fun dayOfWeekFromCode(code: String): DayOfWeek =
    WEEKDAY_CODES.entries.firstOrNull { it.value == code.uppercase() }?.key
        ?: throw IllegalArgumentException("알 수 없는 요일 코드: $code")

/**
 * 태스크 생성 요청. 신규 태스크는 항상 PENDING/TODAY 에서 시작한다(상태는 server 권위).
 * [recurrence] 미지정 시 NONE(단발).
 */
data class TaskCreateRequest(
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:Positive val estimatedMinutes: Int,
    @field:NotNull val date: LocalDate,
    /** 범위 태스크 시작일(선택, #50). 있으면 `startDate <= date`(마감) 이어야 한다. */
    val startDate: LocalDate? = null,
    @field:Size(max = 50) val category: String? = null,
    val eventId: UUID? = null,
    @field:Valid val recurrence: RecurrenceDto? = null,
) {
    fun toEntity(userId: UUID): Task = Task(
        userId = userId,
        title = title,
        estimatedMinutes = estimatedMinutes,
        date = date,
        startDate = startDate,
        category = category,
        eventId = eventId,
        recurrence = recurrence?.toDomain() ?: RecurrenceRule.none(),
    )
}

data class TaskUpdateRequest(
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:Positive val estimatedMinutes: Int,
    @field:NotNull val date: LocalDate,
    /** 범위 태스크 시작일(선택, #50). null 로 보내면 단일로 되돌린다. */
    val startDate: LocalDate? = null,
    @field:Size(max = 50) val category: String? = null,
    val eventId: UUID? = null,
    @field:Valid val recurrence: RecurrenceDto? = null,
)

/** 완료 처리(실제 소요 시간 기록). 상태 전이(→DONE)는 TaskService 가 가드를 거쳐 수행한다. */
data class TaskCompleteRequest(
    @field:PositiveOrZero val actualMinutes: Int? = null,
)

/** 수동 이월 요청(대상 날짜 지정, FRD 5.3 수동 이월). */
data class TaskCarryRequest(
    @field:NotNull val toDate: LocalDate,
)

data class TaskResponse(
    val id: UUID,
    val title: String,
    val estimatedMinutes: Int,
    val date: LocalDate,
    /** 범위 태스크 시작일(#50). null 이면 단일 날짜. */
    val startDate: LocalDate?,
    val state: TaskState,
    val category: String?,
    val eventId: UUID?,
    val fromDate: LocalDate?,
    val actualMinutes: Int?,
    val recurrence: RecurrenceDto,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(task: Task): TaskResponse = TaskResponse(
            id = task.id,
            title = task.title,
            estimatedMinutes = task.estimatedMinutes,
            date = task.date,
            startDate = task.startDate,
            state = task.state,
            category = task.category,
            eventId = task.eventId,
            fromDate = task.fromDate,
            actualMinutes = task.actualMinutes,
            recurrence = RecurrenceDto.from(task.recurrence),
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
        )
    }
}
