package app.ieoseo.server.debt.repository

import app.ieoseo.server.debt.domain.DebtStatus
import app.ieoseo.server.debt.domain.TimeDebt
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

/**
 * TimeDebt Aggregate 영속화. 상태 전이/이월 규칙은 service 계층에 둔다.
 *
 * 모든 조회는 소유자(userId)로 스코프한다(인증-도메인 §2).
 */
interface TimeDebtRepository : JpaRepository<TimeDebt, UUID> {

    /** 소유자 스코프 단건 조회. */
    fun findByIdAndUserId(id: UUID, userId: UUID): Optional<TimeDebt>

    /** 소유자 스코프 목록. */
    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<TimeDebt>

    fun findAllByUserIdAndStatus(userId: UUID, status: DebtStatus, pageable: Pageable): Page<TimeDebt>

    /** 원래 날짜 범위로 부채 조회(주간 요약 등, 소유자 스코프). */
    fun findAllByUserIdAndOriginDateBetween(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
        pageable: Pageable,
    ): Page<TimeDebt>

    /** 주간 요약 집계용(페이지 없이 전체 범위, 소유자 스코프). */
    fun findAllByUserIdAndOriginDateBetween(userId: UUID, from: LocalDate, to: LocalDate): List<TimeDebt>

    fun findAllByUserIdAndTaskId(userId: UUID, taskId: UUID): List<TimeDebt>

    /**
     * 소유자의 모든 부채가 가리키는 `taskId` 목록(중복 부채 방지용 일괄 조회).
     * 부채 생성 잡이 태스크마다 존재 여부를 조회하던 N+1 을 1쿼리로 대체한다.
     */
    @Query("select d.taskId from TimeDebt d where d.userId = :userId")
    fun findTaskIdsByUserId(@Param("userId") userId: UUID): List<UUID>
}
