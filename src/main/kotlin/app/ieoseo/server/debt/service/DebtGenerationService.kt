package app.ieoseo.server.debt.service

import app.ieoseo.server.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * 자정 부채 생성 잡 (#55, FRD 5.3 "미룬 시간").
 *
 * 어제까지 미완료(DONE/ABANDONED 가 아닌)로 남은 태스크를 전 사용자에 걸쳐 감지하고,
 * 태스크 예상시간만큼 [app.ieoseo.server.debt.domain.TimeDebt] 를 만든 뒤 `DEBT_CREATED` 알림을 보낸다.
 * 사용자별 처리·중복 방지·영속화는 [UserDebtGenerator] 가 담당한다.
 *
 * 트랜잭션 격리(B-2): 잡 전체를 한 트랜잭션으로 묶지 않는다. 각 사용자는 [UserDebtGenerator] 의
 * 독립 트랜잭션으로 처리되고, 한 사용자가 실패해도 잡은 다음 사용자로 **계속 진행**한다
 * (전 사용자 단일 트랜잭션이라 한 명 실패가 전체를 롤백하던 문제 해소).
 *
 * 잡 로직은 [run] 에 있고 [scheduled] 는 그 메서드를 호출만 한다(테스트는 [run] 직접 호출).
 * 크론은 `ieoseo.schedule.debt-generation`(기본 매일 00:05) 으로 주입한다.
 */
@Service
class DebtGenerationService(
    private val userRepository: UserRepository,
    private val userDebtGenerator: UserDebtGenerator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 기본값 "-" 는 Spring 의 비활성 크론(테스트 컨텍스트 등 프로퍼티 미설정 시 잡을 끈다).
    @Scheduled(cron = "\${ieoseo.schedule.debt-generation:-}", zone = "Asia/Seoul")
    fun scheduled() {
        val created = run(LocalDate.now())
        log.info("DebtGenerationService: {} 건의 미룬 시간(부채)을 생성했습니다", created)
    }

    /**
     * [today] 기준 어제 미완료 태스크를 부채로 전환한다. 생성한 부채 건수를 반환한다(테스트/로깅용).
     * 사용자별로 격리해, 한 사용자 처리 실패가 나머지를 막지 않는다.
     */
    fun run(today: LocalDate): Int {
        val originDate = today.minusDays(1)
        var created = 0
        for (user in userRepository.findAll()) {
            created += try {
                userDebtGenerator.generate(user.id, originDate)
            } catch (e: Exception) {
                log.error("부채 생성 실패(user={}) — 다음 사용자로 계속", user.id, e)
                0
            }
        }
        return created
    }
}
