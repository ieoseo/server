-- 이어서 인증·데이터 소유권 마이그레이션 (Flyway V2) — ADR-0008 / 인증-도메인 §1·§2, 이슈 #30.
-- users 테이블 신설 + events / tasks / time_debts 에 user_id(FK→users.id, NOT NULL) + 인덱스.
-- PostgreSQL 기준. id 는 애플리케이션 생성 UUID, 날짜는 DATE, 감사 시각은 timestamptz.
--
-- 주의: 기존 행이 있는 환경에서는 user_id NOT NULL 추가 전에 시드 사용자로 백필이 필요하다.
-- baseline(V1) 직후 적용되는 신규 환경을 가정한다(데모 데이터는 초기화 또는 시드 귀속).

-- 1. users (User) — 인증-도메인 §1
CREATE TABLE users (
    id            UUID         NOT NULL,
    email         VARCHAR(320) NOT NULL,
    nickname      VARCHAR(20)  NOT NULL,
    provider      VARCHAR(16)  NOT NULL,
    password_hash VARCHAR(100),
    provider_id   VARCHAR(255),
    status        VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_provider_provider_id UNIQUE (provider, provider_id)
);
CREATE INDEX idx_users_email ON users (email);

-- 2. events.user_id — 소유권(인증-도메인 §2)
ALTER TABLE events ADD COLUMN user_id UUID NOT NULL;
ALTER TABLE events
    ADD CONSTRAINT fk_events_user_id FOREIGN KEY (user_id) REFERENCES users (id);
CREATE INDEX idx_events_user_id ON events (user_id);

-- 3. tasks.user_id
ALTER TABLE tasks ADD COLUMN user_id UUID NOT NULL;
ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_user_id FOREIGN KEY (user_id) REFERENCES users (id);
CREATE INDEX idx_tasks_user_id ON tasks (user_id);

-- 4. time_debts.user_id
ALTER TABLE time_debts ADD COLUMN user_id UUID NOT NULL;
ALTER TABLE time_debts
    ADD CONSTRAINT fk_time_debts_user_id FOREIGN KEY (user_id) REFERENCES users (id);
CREATE INDEX idx_time_debts_user_id ON time_debts (user_id);
