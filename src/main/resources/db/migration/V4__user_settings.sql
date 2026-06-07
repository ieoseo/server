-- 이어서 사용자 설정 마이그레이션 (Flyway V4) — FRD 4.11 / 인증-도메인, 이슈 #56.
-- user_settings 테이블 신설. User 와 1:1(user_id 가 PK 겸 FK→users.id).
-- 다크모드는 클라이언트 로컬 테마로 유지하므로 저장하지 않는다.
-- PostgreSQL 기준. 감사 시각은 timestamptz. 기본값은 도메인(UserSettings) 기본과 일치.

CREATE TABLE user_settings (
    user_id              UUID        NOT NULL,
    auto_carry           BOOLEAN     NOT NULL DEFAULT TRUE,
    day_deadline_hour    INTEGER     NOT NULL DEFAULT 0,
    week_start           VARCHAR(8)  NOT NULL DEFAULT 'MON',
    max_daily_minutes    INTEGER     NOT NULL DEFAULT 480,
    pomodoro_focus       INTEGER     NOT NULL DEFAULT 25,
    pomodoro_short_break INTEGER     NOT NULL DEFAULT 5,
    pomodoro_long_break  INTEGER     NOT NULL DEFAULT 15,
    completion_sound     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_user_settings PRIMARY KEY (user_id),
    CONSTRAINT fk_user_settings_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);
