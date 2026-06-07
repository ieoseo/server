package app.ieoseo.server.presentation.task

import app.ieoseo.server.infrastructure.security.AuthPrincipal
import app.ieoseo.server.infrastructure.security.SecurityConfig
import app.ieoseo.server.domain.task.Task
import app.ieoseo.server.domain.task.TaskState
import app.ieoseo.server.application.task.TaskService
import app.ieoseo.server.presentation.common.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

/**
 * TaskController 슬라이스 테스트 (@WebMvcTest + 실제 SecurityConfig).
 *
 * tasks 는 인증 필수(#30) — 미인증 401 과 인증 주체 스코프, 상태 전이 충돌(409) 매핑을 확인한다(서비스는 mock).
 */
@WebMvcTest(TaskController::class)
@Import(SecurityConfig::class, GlobalExceptionHandler::class)
class TaskControllerWebMvcTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var taskService: TaskService

    private val userId = UUID.randomUUID()

    private fun asUser(): Authentication =
        UsernamePasswordAuthenticationToken(AuthPrincipal(userId, "jiwoo@ieoseo.app"), null, emptyList())

    /** Kotlin non-null 파라미터용 eq() 헬퍼(매처가 null 을 반환해 NPE 나는 문제 회피). */
    private fun <T> eqv(value: T): T {
        eq(value)
        return value
    }

    @Test
    fun `완료 처리는 DONE 태스크와 actualMinutes 를 반환한다`() {
        val id = UUID.randomUUID()
        val task = Task(id = id, userId = userId, title = "알고리즘", estimatedMinutes = 60, date = LocalDate.of(2026, 6, 4))
            .apply { state = TaskState.DONE; actualMinutes = 75 }
        `when`(taskService.complete(eqv(userId), eqv(id), eq(75))).thenReturn(task)

        mockMvc.perform(
            post("/api/v1/tasks/{id}/complete", id)
                .with(authentication(asUser()))
                .contentType(MediaType.APPLICATION_JSON).content("""{ "actualMinutes": 75 }"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.state").value("DONE"))
            .andExpect(jsonPath("$.data.actualMinutes").value(75))
    }

    @Test
    fun `토큰 없이 접근하면 401 UNAUTHORIZED`() {
        mockMvc.perform(post("/api/v1/tasks/{id}/abandon", UUID.randomUUID()))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `불법 상태 전이는 409 CONFLICT envelope 로 매핑한다`() {
        val id = UUID.randomUUID()
        `when`(taskService.complete(eqv(userId), eqv(id), eq(null)))
            .thenThrow(IllegalStateException("PENDING 에서 DONE 으로 전이할 수 없습니다"))

        mockMvc.perform(post("/api/v1/tasks/{id}/complete", id).with(authentication(asUser())))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CONFLICT"))
    }

    @Test
    fun `생성 시 estimatedMinutes 가 0 이하면 400 VALIDATION_ERROR`() {
        val body = """{ "title": "x", "estimatedMinutes": 0, "date": "2026-06-04" }"""
        mockMvc.perform(
            post("/api/v1/tasks")
                .with(authentication(asUser()))
                .contentType(MediaType.APPLICATION_JSON).content(body),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[0].field").value("estimatedMinutes"))
    }
}
