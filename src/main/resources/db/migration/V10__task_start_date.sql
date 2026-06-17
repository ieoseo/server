-- V10: 태스크 범위(시작~종료) 날짜 지원(#50).
-- 기존 date(NOT NULL)는 마감/종료 앵커로 유지하고, 범위 태스크의 시작일을 nullable 로 추가한다.
-- null 이면 단일 날짜 태스크. 백필 없음(기존 행은 전부 단일 = start_date NULL).
ALTER TABLE tasks ADD COLUMN start_date DATE;
