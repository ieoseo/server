-- V8: 이메일 nullable(ADR-0017). 이메일 미제공 provider(Kakao 등) 지원.
-- Supabase 정체성은 sub(=users.id)이며 email 은 부가 정보. Kakao 는 이메일 동의항목이
-- 사업자 검수 전까지 비어 오므로, NOT NULL 을 제거해 NULL 을 허용한다.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- 기존 unique 제약(uk_users_email)은 유지한다. Postgres 는 UNIQUE 컬럼에 다수의 NULL 을
-- 허용하므로(NULL 은 서로 같지 않음), 이메일 없는 사용자가 여럿이어도 충돌하지 않는다.
