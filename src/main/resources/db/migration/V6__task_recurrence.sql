-- 반복 태스크(FRD 5.4) 컬럼 보강 — Task 엔티티의 @Embedded RecurrenceRule 에 1:1 대응.
-- 반복 기능(이슈 #45/#47)이 엔티티에 추가됐으나 대응 마이그레이션이 누락돼 있어, 실 PostgreSQL
-- 기동 시 ddl-auto: validate 가 컬럼 부재로 실패했다. 본 마이그레이션으로 스키마를 맞춘다.
-- 기존 행은 단발(NONE)·요일 마스크 0 으로 채운다(반복 아님).
ALTER TABLE tasks
    ADD COLUMN recurrence_frequency VARCHAR(16) NOT NULL DEFAULT 'NONE',
    ADD COLUMN recurrence_weekly_days INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN recurrence_month_day INTEGER,
    ADD COLUMN recurrence_year_month INTEGER,
    ADD COLUMN recurrence_year_day INTEGER;
