-- V7: Supabase Auth 전환(ADR-0014). users 를 Supabase sub(=id) 기준으로 단순화.
-- 자체 인증 컬럼/제약 제거. id 는 Supabase JWT sub(UUID)로 앱이 주입(자동생성 아님).
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_provider_provider_id;
ALTER TABLE users DROP COLUMN IF EXISTS provider;
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;
ALTER TABLE users DROP COLUMN IF EXISTS provider_id;
