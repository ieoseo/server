package app.ieoseo.server.user.service

import app.ieoseo.server.global.exception.NotFoundException
import app.ieoseo.server.user.domain.User
import app.ieoseo.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** 사용자 프로필 서비스(Supabase Auth 전환 후). 인증·토큰 발급은 Supabase + Resource Server 가 담당. */
@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepository: UserRepository,
) {
    fun me(userId: UUID): User =
        userRepository.findById(userId).orElseThrow { NotFoundException("User", userId) }

    @Transactional
    fun updateNickname(userId: UUID, nickname: String): User {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User", userId) }
        user.updateNickname(nickname)
        return user
    }

    @Transactional
    fun withdraw(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User", userId) }
        user.withdraw()
    }
}
