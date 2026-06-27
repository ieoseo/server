package app.ieoseo.server.task.service

import app.ieoseo.server.task.domain.RecurrenceFrequency
import app.ieoseo.server.task.domain.Task
import app.ieoseo.server.task.domain.TaskRecurrenceExpander
import app.ieoseo.server.user.repository.UserRepository
import app.ieoseo.server.task.repository.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * 반복 인스턴스 생성 잡 (#55, FRD 5.4).
 *
 * 반복 규칙이 있는 템플릿([Task.recurrence] != NONE)을 전 사용자에 걸쳐 찾아,
 * [TaskRecurrenceExpander] 로 다가오는 기간(today ~ today+horizon)의 구체 인스턴스를 만든다.
 *
 * 중복 방지: 같은 사용자·같은 날짜에 이미 인스턴스(또는 다른 태스크)가 있으면 그 날짜는 건너뛴다
 * (잡 재실행 안전). 인스턴스 자체는 더 이상 반복하지 않는다([TaskRecurrenceExpander]).
 *
 * 잡 로직은 [run] 에 있고 [scheduled] 는 그 메서드를 호출만 한다(테스트는 [run] 직접 호출).
 * 크론은 `ieoseo.schedule.recurrence-expansion`(기본 매일 00:10), 기간은 `ieoseo.schedule.recurrence-horizon-days`.
 */
@Service
class RecurrenceInstanceService(
    private val userRepository: UserRepository,
    private val taskRepository: TaskRepository,
    @param:Value("\${ieoseo.schedule.recurrence-horizon-days:14}")
    private val horizonDays: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 기본값 "-" 는 Spring 의 비활성 크론(테스트 컨텍스트 등 프로퍼티 미설정 시 잡을 끈다).
    @Scheduled(cron = "\${ieoseo.schedule.recurrence-expansion:-}", zone = "Asia/Seoul")
    fun scheduled() {
        val created = run(LocalDate.now())
        log.info("RecurrenceInstanceService: {} 건의 반복 인스턴스를 생성했습니다", created)
    }

    /**
     * [today] 부터 horizon 일까지의 반복 인스턴스를 생성한다. 생성 건수를 반환한다(테스트/로깅용).
     */
    @Transactional
    fun run(today: LocalDate): Int {
        val rangeEnd = today.plusDays(horizonDays)
        var created = 0
        for (user in userRepository.findAll()) {
            created += expandForUser(user.id, today, rangeEnd)
        }
        return created
    }

    private fun expandForUser(userId: UUID, rangeStart: LocalDate, rangeEnd: LocalDate): Int {
        val templates = taskRepository.findAllByUserIdAndRecurrenceFrequencyNot(userId, RecurrenceFrequency.NONE)
        if (templates.isEmpty()) return 0

        val occupied = existingKeys(userId, rangeStart, rangeEnd)
        val toCreate = templates
            .flatMap { TaskRecurrenceExpander.expand(it, rangeStart, rangeEnd) }
            .filter { (it.date to it.title) !in occupied }

        if (toCreate.isEmpty()) return 0
        taskRepository.saveAll(toCreate)
        return toCreate.size
    }

    /**
     * 이미 해당 기간에 존재하는 태스크의 `(날짜, 제목)` 키(중복 방지). 날짜만으로 막으면
     * 같은 날 다른 반복 템플릿(또는 수동 태스크)이 점유했을 때 둘째 템플릿이 영구히 누락된다(F1).
     */
    private fun existingKeys(userId: UUID, rangeStart: LocalDate, rangeEnd: LocalDate): Set<Pair<LocalDate, String>> =
        taskRepository.findAllByUserIdAndDateBetween(userId, rangeStart, rangeEnd, Pageable.unpaged())
            .content
            .map { it.date to it.title }
            .toSet()
}
