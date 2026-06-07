-- 이어서 baseline 스키마 (Flyway V1) — ADR-0007.
-- docs/06-백엔드/엔티티-스키마.md 의 events / tasks / time_debts 에 1:1 대응.
-- PostgreSQL 기준. id 는 애플리케이션 생성 UUID, 날짜는 DATE, 감사 시각은 timestamptz.

-- 1. events (Event) — docs §1
CREATE TABLE events (
    id          UUID         NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    category    VARCHAR(50),
    date        DATE,
    start_date  DATE,
    end_date    DATE,
    pinned      BOOLEAN      NOT NULL,
    memo        VARCHAR(1000),
    color       VARCHAR(16),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_events PRIMARY KEY (id)
);
CREATE INDEX idx_events_date ON events (date);
CREATE INDEX idx_events_pinned ON events (pinned);

-- 2. tasks (Task) — docs §2
CREATE TABLE tasks (
    id                UUID         NOT NULL,
    title             VARCHAR(200) NOT NULL,
    estimated_minutes INTEGER      NOT NULL,
    date              DATE         NOT NULL,
    state             VARCHAR(16)  NOT NULL,
    category          VARCHAR(50),
    event_id          UUID,
    from_date         DATE,
    actual_minutes    INTEGER,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT ck_tasks_estimated_minutes CHECK (estimated_minutes > 0)
);
CREATE INDEX idx_tasks_date ON tasks (date);
CREATE INDEX idx_tasks_state ON tasks (state);
CREATE INDEX idx_tasks_event_id ON tasks (event_id);

-- 3. time_debts (TimeDebt) — docs §4
CREATE TABLE time_debts (
    id              UUID        NOT NULL,
    task_id         UUID        NOT NULL,
    minutes         INTEGER     NOT NULL,
    origin_date     DATE        NOT NULL,
    status          VARCHAR(16) NOT NULL,
    carried_to_date DATE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_time_debts PRIMARY KEY (id),
    CONSTRAINT ck_time_debts_minutes CHECK (minutes > 0)
);
CREATE INDEX idx_time_debts_status ON time_debts (status);
CREATE INDEX idx_time_debts_origin_date ON time_debts (origin_date);
CREATE INDEX idx_time_debts_task_id ON time_debts (task_id);
