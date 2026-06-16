package app.ieoseo.server.calendar.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 캘린더 연동 토큰 컬럼 암호화 컨버터 (B-1, ADR-0025).
 *
 * `CalendarConnection.accessToken`/`refreshToken` 을 DB 저장 전에 **AES-256-GCM** 으로 암호화하고
 * 읽을 때 복호화한다. Spring Boot 의 `SpringBeanContainer` 가 이 `@Converter` 를 Spring bean 으로
 * 관리하므로 키를 생성자 주입받는다.
 *
 * 무중단 마이그레이션: 암호문에는 [MARKER] 접두사를 붙인다. 읽을 때 접두사가 있으면 복호화하고,
 * 없으면(과거 평문 행) 그대로 돌려준다 → 별도 백필 없이 점진 전환된다(쓸 때마다 암호문으로 갱신).
 *
 * 키 미설정(로컬/CI): 암호화를 끄고 평문을 그대로 통과시킨다(경고 1회). 운영은 반드시 키를 주입한다.
 * 키는 base64 인코딩한 32바이트(AES-256). 토큰 값·키는 절대 로깅하지 않는다.
 */
@Component
@Converter
class CalendarTokenEncryptionConverter(
    @Value("\${ieoseo.calendar.token-encryption-key:}") rawKey: String,
) : AttributeConverter<String?, String?> {

    // Hibernate 가 Spring bean 을 못 찾고 no-arg 로 인스턴스화하는 슬라이스 테스트(@DataJpaTest)
    // 폴백 — 키 없이 평문 모드로 동작한다. 실제 앱 컨텍스트는 위 @Value 생성자를 쓴다.
    constructor() : this("")

    private val log = LoggerFactory.getLogger(javaClass)
    private val secretKey: SecretKeySpec? = parseKey(rawKey)
    private val random = SecureRandom()

    init {
        if (secretKey == null) {
            log.warn(
                "캘린더 토큰 암호화 키(ieoseo.calendar.token-encryption-key)가 없어 평문 저장으로 폴백합니다. " +
                    "운영에서는 반드시 키를 주입하세요.",
            )
        }
    }

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null
        val key = secretKey ?: return attribute // 키 없으면 평문 통과
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val cipherText = cipher.doFinal(attribute.toByteArray(Charsets.UTF_8))
        val packed = iv + cipherText // iv(12) || ciphertext+tag
        return MARKER + Base64.getEncoder().encodeToString(packed)
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null
        if (!dbData.startsWith(MARKER)) return dbData // 과거 평문 행 통과
        val key = secretKey
            ?: error("암호화된 토큰을 읽으려면 ieoseo.calendar.token-encryption-key 가 필요합니다")
        val packed = Base64.getDecoder().decode(dbData.substring(MARKER.length))
        val iv = packed.copyOfRange(0, IV_BYTES)
        val cipherText = packed.copyOfRange(IV_BYTES, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun parseKey(rawKey: String): SecretKeySpec? {
        if (rawKey.isBlank()) return null
        val bytes = runCatching { Base64.getDecoder().decode(rawKey.trim()) }
            .getOrElse { throw IllegalStateException("토큰 암호화 키가 올바른 base64 가 아닙니다") }
        require(bytes.size == KEY_BYTES) {
            "토큰 암호화 키는 base64 인코딩한 $KEY_BYTES 바이트여야 합니다(현재 ${bytes.size}바이트)"
        }
        return SecretKeySpec(bytes, "AES")
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val MARKER = "enc:v1:"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BYTES = 32 // AES-256
    }
}
