package app.ieoseo.server.notification.service

import app.ieoseo.server.notification.service.NotificationService
import app.ieoseo.server.event.domain.DDayCalculator
import app.ieoseo.server.event.domain.EventType
import app.ieoseo.server.task.domain.TaskState
import app.ieoseo.server.user.repository.UserRepository
import app.ieoseo.server.event.repository.EventRepository
import app.ieoseo.server.task.repository.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

/**
 * D-Day/스트릭 알림 잡 (#55, FRD 5.6).
 *
 * 전 사용자에 걸쳐
 * - 임박 T1 D-Day 이벤트의 남은 일수를 [DDayCalculator] 로 산출해 `notifyDdayIfAbsent` 호출
 *   (임계 1/3/5/7 판단은 `NotificationRules`(서비스) 책임 — 잡은 남은 일수만 넘긴다).
 * - 오늘까지 연속 완료(DONE) 스트릭을 산출해 `notifyStreakIfAbsent` 호출(7의 배수에서만 저장).
 *
 * 중복 방지: 알림 저장은 [NotificationService] 의 `*IfAbsent` 변형이 (type, refId, body)로 막는다.
 *
 * 잡 로직은 [run] 에 있고 [scheduled] 는 그 메서드를 호출만 한다(테스트는 [run] 직접 호출).
 * 크론은 `ieoseo.schedule.notifications`(기본 매일 09:00) 으로 주입한다.
 */
@Service
class NotificationScheduleService(
    private val userRepository: UserRepository,
    private val eventRepository: EventRepository,
    private val taskRepository: TaskRepository,
    private val notificationService: NotificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 임박 D-Day 알림 후보 상한(일). 이 범위 밖이면 잡이 굳이 규칙에 넘기지 않는다. */
    private val ddayLookaheadDays = 7

    /** 스트릭 산출을 위해 거슬러 살필 최대 일수(스트릭 상한). */
    private val streakWindowDays = 365L

    // 기본값 "-" 는 Spring 의 비활성 크론(테스트 컨텍스트 등 프로퍼티 미설정 시 잡을 끈다).
    @Scheduled(cron = "\${ieoseo.schedule.notifications:-}", zone = "Asia/Seoul")
    fun scheduled() {
        run(LocalDate.now())
        log.info("NotificationScheduleService: D-Day/스트릭 알림 잡을 실행했습니다")
    }

    /**
     * [today] 기준 D-Day 임박 알림과 스트릭 알림을 트리거한다.
     *
     * 트랜잭션 격리(B-2): 잡 전체를 한 트랜잭션으로 묶지 않는다([NotificationService] 의 각 `*IfAbsent`
     * 가 자체 트랜잭션). 사용자별 try/catch 로, 한 사용자 처리 실패가 나머지를 막지 않는다.
     */
    fun run(today: LocalDate) {
        for (user in userRepository.findAll()) {
            try {
                notifyDday(user.id, today)
                notifyStreak(user.id, today)
            } catch (e: Exception) {
                log.error("알림 처리 실패(user={}) — 다음 사용자로 계속", user.id, e)
            }
        }
    }

    private fun notifyDday(userId: UUID, today: LocalDate) {
        val events = eventRepository
            .findAllByUserIdAndType(userId, EventType.T1_DDAY, Pageable.unpaged())
            .content
        for (event in events) {
            val daysRemaining = DDayCalculator.calculate(event, today).daysRemaining ?: continue
            if (daysRemaining in 0..ddayLookaheadDays) {
                notificationService.notifyDdayIfAbsent(userId, event.id, event.title, daysRemaining)
            }
        }
    }

    private fun notifyStreak(userId: UUID, today: LocalDate) {
        val streak = consecutiveDoneStreak(userId, today)
        if (streak > 0) {
            notificationService.notifyStreakIfAbsent(userId, streak)
        }
    }

    /**
     * [today] 부터 거슬러 올라가며 "그 날 완료(DONE) 태스크가 하나라도 있는" 연속 일수를 센다.
     * 끊기는 즉시 중단한다. server 권위 산출(클라이언트 계산 불신).
     */
    private fun consecutiveDoneStreak(userId: UUID, today: LocalDate): Int {
        val windowStart = today.minusDays(streakWindowDays)
        val doneDates = taskRepository
            .findAllByUserIdAndDateBetween(userId, windowStart, today, Pageable.unpaged())
            .content
            .asSequence()
            .filter { it.state == TaskState.DONE }
            .map { it.date }
            .toSet()

        // 잡은 오전에 돌 수 있어 '오늘'은 아직 미완료일 수 있다. 오늘이 완료면 오늘부터,
        // 아니면 어제부터 거슬러 세어 어제까지 이어진 연속을 끊지 않는다(F9).
        var streak = 0
        var cursor = if (today in doneDates) today else today.minusDays(1)
        while (cursor in doneDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
