package app.ieoseo.server.infrastructure.scheduling

import app.ieoseo.server.application.notification.NotificationService
import app.ieoseo.server.domain.debt.TimeDebt
import app.ieoseo.server.domain.task.Task
import app.ieoseo.server.domain.task.TaskState
import app.ieoseo.server.infrastructure.persistence.auth.UserRepository
import app.ieoseo.server.infrastructure.persistence.debt.TimeDebtRepository
import app.ieoseo.server.infrastructure.persistence.task.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * 자정 부채 생성 잡 (#55, FRD 5.3 "미룬 시간").
 *
 * 어제까지 미완료(DONE/ABANDONED 가 아닌)로 남은 태스크를 전 사용자에 걸쳐 감지하고,
 * 태스크 예상시간([Task.estimatedMinutes])만큼 [TimeDebt] 를 만든 뒤 `DEBT_CREATED` 알림을 보낸다.
 *
 * 중복 방지: 같은 태스크([Task.id])로 이미 만든 부채가 있으면 재생성하지 않는다(잡 재실행 안전).
 *
 * 잡 로직은 [run] 에 있고 [scheduled] 는 그 메서드를 호출만 한다(테스트는 [run] 직접 호출).
 * 크론은 `ieoseo.schedule.debt-generation`(기본 매일 00:05) 으로 주입한다.
 */
@Service
class DebtGenerationService(
    private val userRepository: UserRepository,
    private val taskRepository: TaskRepository,
    private val timeDebtRepository: TimeDebtRepository,
    private val notificationService: NotificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 부채로 환산할 미완료 상태(완료/탕감 제외). */
    private val unfinishedStates = listOf(
        TaskState.PENDING,
        TaskState.TODAY,
        TaskState.MISSED,
        TaskState.CARRIED,
        TaskState.OVERDUE,
    )

    // 기본값 "-" 는 Spring 의 비활성 크론(테스트 컨텍스트 등 프로퍼티 미설정 시 잡을 끈다).
    @Scheduled(cron = "\${ieoseo.schedule.debt-generation:-}", zone = "Asia/Seoul")
    fun scheduled() {
        val created = run(LocalDate.now())
        log.info("DebtGenerationService: {} 건의 미룬 시간(부채)을 생성했습니다", created)
    }

    /**
     * [today] 기준 어제 미완료 태스크를 부채로 전환한다. 생성한 부채 건수를 반환한다(테스트/로깅용).
     */
    @Transactional
    fun run(today: LocalDate): Int {
        val yesterday = today.minusDays(1)
        var created = 0
        for (user in userRepository.findAll()) {
            created += generateForUser(user.id, yesterday)
        }
        return created
    }

    private fun generateForUser(userId: UUID, originDate: LocalDate): Int {
        val unfinished = taskRepository.findAllByUserIdAndDateAndStateIn(userId, originDate, unfinishedStates)
        var created = 0
        for (task in unfinished) {
            if (hasDebt(userId, task.id)) continue // 중복 방지
            timeDebtRepository.save(
                TimeDebt(
                    userId = userId,
                    taskId = task.id,
                    minutes = task.estimatedMinutes,
                    originDate = originDate,
                ),
            )
            notificationService.notifyDebtCreated(userId, task.id, task.title, task.estimatedMinutes)
            created++
        }
        return created
    }

    private fun hasDebt(userId: UUID, taskId: UUID): Boolean =
        timeDebtRepository.findAllByUserIdAndTaskId(userId, taskId).isNotEmpty()
}
