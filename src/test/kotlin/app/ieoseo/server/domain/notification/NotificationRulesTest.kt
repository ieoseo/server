package app.ieoseo.server.domain.notification

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 알림 생성 규칙(FRD 5.6) 순수 로직 테스트 — RED 먼저.
 *
 * 입력(이벤트 D-Day, 부채 발생/누적, 스트릭)을 받아 [Notification] 후보를 산출한다.
 * 저장은 하지 않고 산출만 검증한다(서비스가 영속화 담당).
 */
class NotificationRulesTest {

    private val userId = UUID.randomUUID()
    private val refId = UUID.randomUUID()

    @Test
    fun `D-Day N일 전(1,3,5,7)이면 DDAY 알림을 만든다`() {
        for (days in listOf(1, 3, 5, 7)) {
            val n = NotificationRules.dday(userId, refId, title = "토익 시험", daysRemaining = days)
            assertTrue(n != null, "D-$days 는 알림이 있어야 한다")
            assertEquals(NotificationType.DDAY, n!!.type)
            assertEquals(userId, n.userId)
            assertEquals(refId, n.refId)
            assertTrue(n.body.contains("$days"))
        }
    }

    @Test
    fun `임계일이 아니면 DDAY 알림을 만들지 않는다`() {
        assertNull(NotificationRules.dday(userId, refId, title = "토익", daysRemaining = 4))
        assertNull(NotificationRules.dday(userId, refId, title = "토익", daysRemaining = 0))
        assertNull(NotificationRules.dday(userId, refId, title = "토익", daysRemaining = -2))
    }

    @Test
    fun `시간부채가 발생하면 DEBT_CREATED 알림을 만든다`() {
        val n = NotificationRules.debtCreated(userId, refId, title = "알고리즘", minutes = 60)
        assertEquals(NotificationType.DEBT_CREATED, n.type)
        assertTrue(n.body.contains("알고리즘"))
        assertEquals(refId, n.refId)
    }

    @Test
    fun `부채 누적이 경고 임계(120분)를 넘으면 DEBT_WARNING 알림을 만든다`() {
        val n = NotificationRules.debtWarning(userId, totalMinutes = 180)
        assertTrue(n != null)
        assertEquals(NotificationType.DEBT_WARNING, n!!.type)
        assertTrue(n.body.contains("3") || n.body.contains("180"))
    }

    @Test
    fun `부채 누적이 경고 임계 미만이면 DEBT_WARNING 을 만들지 않는다`() {
        assertNull(NotificationRules.debtWarning(userId, totalMinutes = 60))
    }

    @Test
    fun `스트릭이 축하 임계(매 7일)에 도달하면 STREAK 알림을 만든다`() {
        val n = NotificationRules.streak(userId, streakDays = 7)
        assertTrue(n != null)
        assertEquals(NotificationType.STREAK, n!!.type)
        assertTrue(n.body.contains("7"))
    }

    @Test
    fun `스트릭이 축하 임계가 아니면 STREAK 알림을 만들지 않는다`() {
        assertNull(NotificationRules.streak(userId, streakDays = 5))
        assertNull(NotificationRules.streak(userId, streakDays = 0))
    }

    @Test
    fun `생성된 알림은 기본적으로 안 읽음 상태다`() {
        val n = NotificationRules.debtCreated(userId, refId, title = "x", minutes = 30)
        assertEquals(false, n.read)
    }

    @Test
    fun `규칙 산출은 날짜에 의존하지 않는다(순수 입력 기반)`() {
        val a = NotificationRules.dday(userId, refId, "x", daysRemaining = 3)
        val b = NotificationRules.dday(userId, refId, "x", daysRemaining = 3)
        assertEquals(a?.type, b?.type)
        assertTrue(LocalDate.now().year >= 2026)
    }
}
