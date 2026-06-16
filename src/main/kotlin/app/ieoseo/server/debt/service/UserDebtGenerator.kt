package app.ieoseo.server.debt.service

import app.ieoseo.server.debt.domain.TimeDebt
import app.ieoseo.server.debt.repository.TimeDebtRepository
import app.ieoseo.server.notification.service.NotificationService
import app.ieoseo.server.task.domain.TaskState
import app.ieoseo.server.task.repository.TaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * 한 사용자의 자정 부채 생성 단위 (B-2).
 *
 * [DebtGenerationService] 가 사용자별로 호출한다. **사용자 단위 트랜잭션 경계**라 한 사용자의 처리가
 * 실패해도 다른 사용자에게 번지지 않는다(잡 오케스트레이터가 try/catch 로 격리). 또 기존 부채의
 * `taskId` 를 **한 번에** 조회해(중복 방지) 태스크마다 조회하던 N+1 을 없앴다.
 */
@Service
class UserDebtGenerator(
    private val taskRepository: TaskRepository,
    private val timeDebtRepository: TimeDebtRepository,
    private val notificationService: NotificationService,
) {
    /** 부채로 환산할 미완료 상태(완료/탕감 제외). */
    private val unfinishedStates = listOf(
        TaskState.PENDING,
        TaskState.TODAY,
        TaskState.MISSED,
        TaskState.CARRIED,
        TaskState.OVERDUE,
    )

    /**
     * [userId] 의 [originDate] 미완료 태스크를 부채로 전환한다. 생성한 건수를 반환한다.
     * 이미 부채가 있는 태스크는 건너뛴다(잡 재실행 안전).
     */
    @Transactional
    fun generate(userId: UUID, originDate: LocalDate): Int {
        val unfinished = taskRepository.findAllByUserIdAndDateAndStateIn(userId, originDate, unfinishedStates)
        if (unfinished.isEmpty()) return 0

        // N+1 제거: 기존 부채의 taskId 를 1쿼리로 모아 메모리 대조한다.
        val existingTaskIds = timeDebtRepository.findTaskIdsByUserId(userId).toHashSet()

        var created = 0
        for (task in unfinished) {
            if (task.id in existingTaskIds) continue
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
}
