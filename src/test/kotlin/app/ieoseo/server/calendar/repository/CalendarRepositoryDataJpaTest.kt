package app.ieoseo.server.calendar.repository

import app.ieoseo.server.calendar.domain.CalendarConnection
import app.ieoseo.server.calendar.domain.CalendarProvider
import app.ieoseo.server.calendar.domain.ExternalEvent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Calendar Repository 소유권 스코프 슬라이스 테스트 (@DataJpaTest + H2, 이슈 #59).
 *
 * 연결/외부 일정은 userId 로만 스코프되고, upsert 키·날짜 범위 조회가 동작함을 검증한다.
 */
@DataJpaTest
@ImportAutoConfiguration(DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class)
@TestPropertySource(
    properties = [
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class CalendarRepositoryDataJpaTest {

    @Autowired
    lateinit var connectionRepository: CalendarConnectionRepository

    @Autowired
    lateinit var externalEventRepository: ExternalEventRepository

    private val owner = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Test
    fun `연결 목록은 userId 로만 스코프된다`() {
        connectionRepository.save(CalendarConnection(userId = owner, provider = CalendarProvider.GOOGLE))
        connectionRepository.save(CalendarConnection(userId = owner, provider = CalendarProvider.NOTION))
        connectionRepository.save(CalendarConnection(userId = other, provider = CalendarProvider.GOOGLE))

        assertEquals(2, connectionRepository.findAllByUserId(owner).size)
    }

    @Test
    fun `연결 단건은 소유자·provider 로 조회된다`() {
        connectionRepository.save(CalendarConnection(userId = owner, provider = CalendarProvider.GOOGLE))

        assertTrue(connectionRepository.findByUserIdAndProvider(owner, CalendarProvider.GOOGLE).isPresent)
        assertTrue(connectionRepository.findByUserIdAndProvider(other, CalendarProvider.GOOGLE).isEmpty)
    }

    @Test
    fun `외부 일정은 upsert 키로 조회되고 날짜 범위로 스코프된다`() {
        externalEventRepository.save(ext("g1", LocalDate.of(2026, 6, 4)))
        externalEventRepository.save(ext("g2", LocalDate.of(2026, 6, 20)))
        externalEventRepository.save(ext("g3", LocalDate.of(2026, 7, 1), userId = other))

        assertTrue(
            externalEventRepository
                .findByUserIdAndProviderAndExternalId(owner, CalendarProvider.GOOGLE, "g1")
                .isPresent,
        )
        val june = externalEventRepository.findAllByUserIdAndDateBetween(
            owner,
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 30),
        )
        assertEquals(2, june.size) // 타인(other) 7월 일정 제외
    }

    @Test
    fun `연결 해제 시 provider 외부 일정을 일괄 삭제한다`() {
        externalEventRepository.save(ext("g1", LocalDate.of(2026, 6, 4)))
        externalEventRepository.save(
            ext("n1", LocalDate.of(2026, 6, 4), provider = CalendarProvider.NOTION),
        )

        externalEventRepository.deleteAllByUserIdAndProvider(owner, CalendarProvider.GOOGLE)

        val remaining = externalEventRepository.findAllByUserIdAndDateBetween(
            owner,
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 30),
        )
        assertEquals(1, remaining.size)
        assertEquals(CalendarProvider.NOTION, remaining[0].provider)
    }

    private fun ext(
        externalId: String,
        date: LocalDate,
        userId: UUID = owner,
        provider: CalendarProvider = CalendarProvider.GOOGLE,
    ): ExternalEvent = ExternalEvent(
        userId = userId,
        provider = provider,
        externalId = externalId,
        title = "외부 일정",
        date = date,
    )
}
