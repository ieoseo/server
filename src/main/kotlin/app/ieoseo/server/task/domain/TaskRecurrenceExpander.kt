package app.ieoseo.server.task.domain

import java.time.LocalDate

/**
 * 반복 태스크 인스턴스 펼치기 도메인 서비스 (FRD 5.4).
 *
 * 반복 템플릿 [Task] 와 날짜 범위(예: 특정 주)를 받아, 규칙([Task.recurrence])이 만들어내는
 * 구체 Task 인스턴스 목록을 산출하는 **순수 함수**다(테스트 가능, server 권위).
 *
 * 규칙:
 * - 각 발생일을 [Task.date] 로 갖는 새 Task 를 만든다(상태 PENDING — 신규는 항상 server 권위로 시작).
 * - 인스턴스는 더 이상 반복하지 않는다([RecurrenceRule.none]) — 무한 재펼침 방지.
 * - title·estimatedMinutes·category·eventId 는 템플릿에서 복사한다.
 *
 * 정책: 실시간 스케줄러/배치는 범위 밖. 온디맨드(조회 시 확장) 또는 명시적 생성 시 이 함수를 사용한다.
 * 템플릿 규칙 수정은 "이후 생성분에만" 반영되고 이미 생성된 인스턴스는 불변이다.
 */
object TaskRecurrenceExpander {

    fun expand(template: Task, rangeStart: LocalDate, rangeEnd: LocalDate): List<Task> =
        template.recurrence
            .expand(anchor = template.date, rangeStart = rangeStart, rangeEnd = rangeEnd)
            .map { occurrence -> instanceOn(template, occurrence) }

    private fun instanceOn(template: Task, date: LocalDate): Task = Task(
        userId = template.userId,
        title = template.title,
        estimatedMinutes = template.estimatedMinutes,
        date = date,
        state = TaskState.PENDING,
        category = template.category,
        eventId = template.eventId,
        recurrence = RecurrenceRule.none(),
    )
}
