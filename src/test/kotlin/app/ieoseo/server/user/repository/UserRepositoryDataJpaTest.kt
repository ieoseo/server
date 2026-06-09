package app.ieoseo.server.user.repository

import app.ieoseo.server.user.domain.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * UserRepository 슬라이스 테스트 (@DataJpaTest + 임베디드 H2).
 *
 * Supabase Auth 전환 후 User 는 id(=Supabase sub)·email·nickname 만 갖는다(ADR-0014).
 * 기본 test 프로파일은 DataSource/JPA auto-config 를 제외하므로, 본 슬라이스는 해당 제외를
 * 비우고 H2 임베디드 DB 로 기본 CRUD(save/findById/existsById)를 검증한다.
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
    fun `save 한 사용자를 id 로 조회한다`() {
        val id = UUID.randomUUID()
        repository.save(User(id = id, email = "jiwoo@ieoseo.app", nickname = "지우"))

        val found = repository.findById(id).orElse(null)

        assertEquals("jiwoo@ieoseo.app", found?.email)
        assertEquals("지우", found?.nickname)
    }

    @Test
    fun `id 존재 여부를 확인한다`() {
        val id = UUID.randomUUID()
        repository.save(User(id = id, email = "taken@ieoseo.app", nickname = "지우"))

        assertTrue(repository.existsById(id))
        assertFalse(repository.existsById(UUID.randomUUID()))
    }
}
