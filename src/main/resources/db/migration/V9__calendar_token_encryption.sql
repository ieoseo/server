-- V9: 캘린더 토큰 컬럼 암호화 대비 폭 확장(B-1, ADR-0025).
-- access_token/refresh_token 을 AES-256-GCM 으로 암호화하면 base64(iv+ciphertext+tag) +
-- 마커 접두 때문에 평문보다 길어진다. 기존 VARCHAR(2048) 로는 긴 토큰의 암호문이 잘릴 수 있어
-- 4096 으로 넓힌다. 데이터 변환(백필)은 없다 — 컨버터가 마커 유무로 평문/암호문을 구분해
-- 점진 전환하므로 기존 행은 그대로 두고, 다음 쓰기부터 암호문으로 저장된다.
ALTER TABLE calendar_connections ALTER COLUMN access_token TYPE VARCHAR(4096);
ALTER TABLE calendar_connections ALTER COLUMN refresh_token TYPE VARCHAR(4096);
