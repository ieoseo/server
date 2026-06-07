package app.ieoseo.server.infrastructure.persistence.auth

import app.ieoseo.server.domain.auth.AuthProvider
import app.ieoseo.server.domain.auth.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * UserRepository 슬라이스 테스트 (@DataJpaTest + 임베디드 H2).
 *
 * 기본 test 프로파일은 DataSource/JPA auto-config 를 제외하므로(실 DB 미연결),
 * 본 슬라이스는 해당 제외를 비우고 H2 임베디드 DB 로 쿼리 메서드를 검증한다.
 */
@DataJpaTest
@ImportAutoConfiguration(DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class)
@TestPropertySource(
    properties = [
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ],
)
class UserRepositoryDataJpaTest {

    @Autowired
    lateinit var repository: UserRepository

    @Test
    fun `email 로 사용자를 조회한다`() {
        repository.save(localUser("jiwoo@ieoseo.app"))

        val found = repository.findByEmail("jiwoo@ieoseo.app")

        assertEquals("jiwoo@ieoseo.app", found?.email)
    }

    @Test
    fun `없는 email 은 null 을 반환한다`() {
        assertNull(repository.findByEmail("nobody@ieoseo.app"))
    }

    @Test
    fun `email 존재 여부를 확인한다`() {
        repository.save(localUser("taken@ieoseo.app"))

        assertTrue(repository.existsByEmail("taken@ieoseo.app"))
        assertFalse(repository.existsByEmail("free@ieoseo.app"))
    }

    @Test
    fun `provider 와 providerId 로 소셜 사용자를 조회한다`() {
        repository.save(
            User(
                email = "social@ieoseo.app",
                nickname = "소셜",
                provider = AuthProvider.GOOGLE,
                providerId = "google-sub-1",
            ),
        )

        val found = repository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-1")

        assertEquals("social@ieoseo.app", found?.email)
    }

    private fun localUser(email: String): User = User(
        email = email,
        nickname = "지우",
        provider = AuthProvider.LOCAL,
        passwordHash = "\$2a\$10\$abcdefghijklmnopqrstuv",
    )
}
