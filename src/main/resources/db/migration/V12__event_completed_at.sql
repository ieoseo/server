-- V12: 이벤트 종료(완료) 처리 지원(FRD 5.1).
-- D-Day/기간이 지나도(D+) 자동 삭제하지 않고, 유저가 명시적으로 "종료 처리"할 때 시각을 기록한다.
-- null = 미종료(진행/경과, 홈에 계속 노출), 값 있음 = 종료(완료, 홈 목록에서 숨김). 백필 없음.
ALTER TABLE events ADD COLUMN completed_at TIMESTAMPTZ;
