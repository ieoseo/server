-- public 테이블 RLS(Row Level Security) 활성화 — Supabase PostgREST(anon) 직접 노출 차단.
--
-- 배경: Supabase 는 public 스키마 테이블을 anon key(앱에 박힌 공개값)로 접근되는 REST API 로
-- 자동 노출한다. RLS 가 꺼져 있으면 서버를 우회해 전 사용자 데이터에 직접 접근할 수 있다.
-- 데이터 접근은 전부 Spring 서버(테이블 owner role = RLS 우회) 경유이고, client 는 public
-- 테이블을 Supabase REST 로 직접 읽지 않으므로 정책(policy)은 두지 않는다(= anon 전면 차단).
--
-- 테스트는 @DataJpaTest(H2)+ddl-auto 로 돌아 이 마이그레이션을 실행하지 않는다.
--
-- 컨벤션: 앞으로 새 public 테이블을 만드는 마이그레이션은 RLS 활성화를 함께 포함한다(ADR-0027).
--
-- 주의: Flyway 내부 테이블(flyway_schema_history)에는 여기서 RLS 를 걸지 않는다. 마이그레이션
-- 도중 Flyway 가 그 테이블을 잠그고 있어, 같은 테이블에 ACCESS EXCLUSIVE 가 필요한 ENABLE RLS
-- 를 걸면 자기 자신과 락 충돌로 무한 대기한다(fresh DB 에서 부팅 멈춤). 이력 테이블 노출 차단이
-- 필요하면 Flyway 밖(수동/별도 경로)에서 적용한다.

ALTER TABLE public.tasks                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.events                ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.calendar_connections  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.external_events       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.time_debts            ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.users                 ENABLE ROW LEVEL SECURITY;
