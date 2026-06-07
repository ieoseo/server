-- 이어서 인앱 알림 마이그레이션 (Flyway V3) — FRD 5.6 / 알림-도메인, 이슈 #46.
-- notifications 테이블 신설 + user_id(FK→users.id, NOT NULL) + 소유권/안읽음 인덱스.
-- PostgreSQL 기준. id 는 애플리케이션 생성 UUID, 감사 시각은 timestamptz.
-- OS 푸시(FCM/APNs)는 범위 외(후속) — 디바이스 토큰/구독 테이블은 본 마이그레이션에 없다.

CREATE TABLE notifications (
    id         UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    type       VARCHAR(24)  NOT NULL,
    title      VARCHAR(120) NOT NULL,
    body       VARCHAR(280) NOT NULL,
    ref_id     UUID,
    read       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 소유자 스코프 목록(최신순) + 안읽음 카운트 가속.
CREATE INDEX idx_notifications_user_id ON notifications (user_id);
CREATE INDEX idx_notifications_user_read ON notifications (user_id, read);
