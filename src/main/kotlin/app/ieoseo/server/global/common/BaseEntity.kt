package app.ieoseo.server.global.common

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.Instant

/**
 * 생성/수정 시각 감사(audit) 컬럼을 공유하는 베이스.
 * `@MappedSuperclass` 라 별도 테이블이 생기지 않고 상속 엔티티 테이블에 컬럼으로 합쳐진다.
 */
@MappedSuperclass
abstract class BaseEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    @PrePersist
    protected fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = Instant.now()
    }
}
