package app.ieoseo.server.calendar.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * [CalendarTokenEncryptionConverter] 단위 테스트 (B-1).
 *
 * 키를 직접 주입해 Spring 컨텍스트 없이 암복호화 행위를 검증한다.
 */
class CalendarTokenEncryptionConverterTest {

    // 테스트 전용 32바이트(AES-256) base64 키. 운영 키 아님.
    private val testKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val converter = CalendarTokenEncryptionConverter(testKey)
    private val plaintextConverter = CalendarTokenEncryptionConverter("")

    @Test
    fun `암호화 후 복호화하면 원문이 복원된다`() {
        val token = "ya29.a0AfH-very-secret-access-token"

        val stored = converter.convertToDatabaseColumn(token)
        val loaded = converter.convertToEntityAttribute(stored)

        assertThat(loaded).isEqualTo(token)
    }

    @Test
    fun `DB 저장 값은 평문이 아니라 마커 접두 암호문이다`() {
        val token = "refresh-token-value"

        val stored = converter.convertToDatabaseColumn(token)

        assertThat(stored).isNotNull()
        assertThat(stored).startsWith("enc:v1:")
        assertThat(stored).doesNotContain(token)
    }

    @Test
    fun `같은 평문도 매번 다른 암호문이 된다(IV 무작위)`() {
        val token = "same-token"

        val a = converter.convertToDatabaseColumn(token)
        val b = converter.convertToDatabaseColumn(token)

        assertThat(a).isNotEqualTo(b)
        assertThat(converter.convertToEntityAttribute(a)).isEqualTo(token)
        assertThat(converter.convertToEntityAttribute(b)).isEqualTo(token)
    }

    @Test
    fun `과거 평문 행(마커 없음)은 그대로 읽힌다`() {
        val legacyPlaintext = "legacy-token-without-marker"

        val loaded = converter.convertToEntityAttribute(legacyPlaintext)

        assertThat(loaded).isEqualTo(legacyPlaintext)
    }

    @Test
    fun `null 은 양방향 모두 null 로 통과한다`() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
        assertThat(converter.convertToEntityAttribute(null)).isNull()
    }

    @Test
    fun `키가 없으면 평문으로 통과한다(로컬 폴백)`() {
        val token = "no-key-token"

        val stored = plaintextConverter.convertToDatabaseColumn(token)

        assertThat(stored).isEqualTo(token)
        assertThat(plaintextConverter.convertToEntityAttribute(token)).isEqualTo(token)
    }

    @Test
    fun `잘못된 길이의 키는 시작 시점에 거부된다`() {
        val shortKey = Base64.getEncoder().encodeToString(ByteArray(16))

        assertThatThrownBy { CalendarTokenEncryptionConverter(shortKey) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `다른 키로 복호화하면 토큰을 노출하지 않는 명확한 예외로 감싼다(C6)`() {
        val otherKey = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 7).toByte() })
        val otherConverter = CalendarTokenEncryptionConverter(otherKey)
        val stored = converter.convertToDatabaseColumn("ya29.super-secret-token")

        assertThatThrownBy { otherConverter.convertToEntityAttribute(stored) }
            .isInstanceOf(CalendarTokenDecryptException::class.java)
            .hasMessageNotContaining("super-secret")
    }
}
