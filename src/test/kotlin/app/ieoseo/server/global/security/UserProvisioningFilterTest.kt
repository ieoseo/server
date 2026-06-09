package app.ieoseo.server.global.security

import app.ieoseo.server.user.domain.User
import app.ieoseo.server.user.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * UserProvisioningFilter 단위 테스트(ADR-0017).
 *
 * 인증 주체가 없으면 User 행을 생성(없을 때만)하며, 이메일 미제공 provider 는 placeholder 없이
 * email=null 로 저장하고 닉네임은 표시 이름→이메일 local-part→"user" 순으로 정한다.
 */
class UserProvisioningFilterTest {

    private val repo: UserRepository = mock(UserRepository::class.java)

    @Suppress("UNCHECKED_CAST")
    private val provider: ObjectProvider<UserRepository> =
        mock(ObjectProvider::class.java) as ObjectProvider<UserRepository>

    private val filter = UserProvisioningFilter(provider)
    private val chain: FilterChain = mock(FilterChain::class.java)
    private val request: HttpServletRequest = mock(HttpServletRequest::class.java)
    private val response: HttpServletResponse = mock(HttpServletResponse::class.java)
    private val userId = UUID.randomUUID()

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun authenticate(principal: AuthPrincipal) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, emptyList())
        `when`(provider.getIfAvailable()).thenReturn(repo)
    }

    private fun savedUser(): User {
        val captor = ArgumentCaptor.forClass(User::class.java)
        verify(repo).save(captor.capture())
        return captor.value
    }

    @Test
    fun `이메일 있는 신규 사용자는 email 과 local-part 닉네임으로 생성된다`() {
        authenticate(AuthPrincipal(userId, email = "jiwoo@ieoseo.app"))
        `when`(repo.existsById(userId)).thenReturn(false)

        filter.doFilter(request, response, chain)

        val user = savedUser()
        assertEquals(userId, user.id)
        assertEquals("jiwoo@ieoseo.app", user.email)
        assertEquals("jiwoo", user.nickname)
    }

    @Test
    fun `이메일 없고 표시 이름이 있으면 email null 에 이름으로 닉네임을 만든다`() {
        authenticate(AuthPrincipal(userId, email = null, name = "카카오지우"))
        `when`(repo.existsById(userId)).thenReturn(false)

        filter.doFilter(request, response, chain)

        val user = savedUser()
        assertNull(user.email)
        assertEquals("카카오지우", user.nickname)
    }

    @Test
    fun `이메일도 이름도 없으면 닉네임은 user`() {
        authenticate(AuthPrincipal(userId, email = null, name = null))
        `when`(repo.existsById(userId)).thenReturn(false)

        filter.doFilter(request, response, chain)

        val user = savedUser()
        assertNull(user.email)
        assertEquals("user", user.nickname)
    }

    @Test
    fun `이미 존재하면 저장하지 않는다`() {
        authenticate(AuthPrincipal(userId, email = "jiwoo@ieoseo.app"))
        `when`(repo.existsById(userId)).thenReturn(true)

        filter.doFilter(request, response, chain)

        verify(repo, never()).save(org.mockito.ArgumentMatchers.any(User::class.java))
    }

    @Test
    fun `인증 주체가 없으면 아무 것도 하지 않고 체인을 진행한다`() {
        filter.doFilter(request, response, chain)

        verify(repo, never()).save(org.mockito.ArgumentMatchers.any(User::class.java))
        verify(chain).doFilter(request, response)
    }
}
