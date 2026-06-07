-- 이어서 외부 캘린더 동기화 마이그레이션 (Flyway V5) — FRD 4.5 / 4.12 / 5.7, 이슈 #59 / ADR-0010.
-- calendar_connections(연결·토큰) + external_events(읽기 전용 외부 일정) 신설.
-- PostgreSQL 기준. id 는 애플리케이션 생성 UUID, date 는 DATE(ISO ymd), 감사 시각은 timestamptz.
--
-- 토큰 민감도: access_token/refresh_token 은 민감 정보다. 본 트랙은 평문 컬럼 저장하되
-- 운영에서는 컬럼 암호화(또는 KMS/secret 저장소) 적용을 권장한다(ADR-0010).
-- Notion 은 refresh 개념이 없어 refresh_token 에 database id 를 보관한다(본 트랙 단순화).

-- 1. calendar_connections (CalendarConnection) — 사용자·provider 당 1개
CREATE TABLE calendar_connections (
    id             UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    provider       VARCHAR(16)  NOT NULL,
    access_token   VARCHAR(2048),
    refresh_token  VARCHAR(2048),
    expires_at     TIMESTAMPTZ,
    status         VARCHAR(16)  NOT NULL,
    last_synced_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_calendar_connections PRIMARY KEY (id),
    CONSTRAINT uk_calendar_connections_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_calendar_connections_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_calendar_connections_user_id ON calendar_connections (user_id);

-- 2. external_events (ExternalEvent) — 동기화로 적재되는 읽기 전용 외부 일정
CREATE TABLE external_events (
    id          UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    provider    VARCHAR(16)  NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    title       VARCHAR(500) NOT NULL,
    date        DATE         NOT NULL,
    time        VARCHAR(16),
    read_only   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_external_events PRIMARY KEY (id),
    CONSTRAINT uk_external_events_user_provider_external UNIQUE (user_id, provider, external_id),
    CONSTRAINT fk_external_events_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_external_events_user_id ON external_events (user_id);
CREATE INDEX idx_external_events_date ON external_events (date);
