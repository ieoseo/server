# Changelog

## [1.1.0](https://github.com/pkdee/daykit/compare/server-v1.0.0...server-v1.1.0) (2026-06-05)


### Features

* **server:** Flyway 배선 + Dockerfile + Azure 배포 가이드 ([#70](https://github.com/pkdee/daykit/issues/70)) ([48d7c32](https://github.com/pkdee/daykit/commit/48d7c329367cd5bd8a07cb5248e9ea06999b8ecc))
* **server:** 데이터 소유권(user_id) + events/tasks/debts 강제 인증 ([#31](https://github.com/pkdee/daykit/issues/31)) ([c765aaa](https://github.com/pkdee/daykit/commit/c765aaae792d1e1df7fc8a27b064bd88a529957d)), closes [#30](https://github.com/pkdee/daykit/issues/30)
* **server:** 소셜 OAuth 검증(Google/Apple/Kakao) + /auth/oauth/{provider} ([#39](https://github.com/pkdee/daykit/issues/39)) ([02081c4](https://github.com/pkdee/daykit/commit/02081c4abbd855722932cb30f5f4e989cae96efc)), closes [#37](https://github.com/pkdee/daykit/issues/37)
* **server:** 스케줄러/트리거 — 부채 자동생성·반복 인스턴스·알림 ([#57](https://github.com/pkdee/daykit/issues/57)) ([0ebaa66](https://github.com/pkdee/daykit/commit/0ebaa66184ca43744173eb6f713c89d7752fb661)), closes [#55](https://github.com/pkdee/daykit/issues/55)
* **server:** 이메일 인증·JWT·Spring Security 구현 ([#28](https://github.com/pkdee/daykit/issues/28)) ([cdc7c16](https://github.com/pkdee/daykit/commit/cdc7c16864ddf43ea7e900cef987ab4bfeec7007)), closes [#27](https://github.com/pkdee/daykit/issues/27)
* 계정/설정 실연동 — 회원탈퇴·프로필 수정·사용자 설정 ([#58](https://github.com/pkdee/daykit/issues/58)) ([e407d81](https://github.com/pkdee/daykit/commit/e407d81deeea0b0a8ee11a88abf2eb64005130a9)), closes [#56](https://github.com/pkdee/daykit/issues/56)
* 관측성(Sentry) 연동 (server 코어 SDK + client sentry_flutter) ([#64](https://github.com/pkdee/daykit/issues/64)) ([a30747a](https://github.com/pkdee/daykit/commit/a30747af7f880d5c6350be7d9d19af9b6018ccdd))
* 미룬 시간(부채) UX 마무리 — 제목 표시 + 자동 이월 배선 ([#44](https://github.com/pkdee/daykit/issues/44)) ([021bdd5](https://github.com/pkdee/daykit/commit/021bdd522439c735b84cb7157c95858ddde78b45)), closes [#41](https://github.com/pkdee/daykit/issues/41)
* 반복 태스크(주/월/연) — 도메인·API·클라 입력 연동 ([#47](https://github.com/pkdee/daykit/issues/47)) ([a3ad43f](https://github.com/pkdee/daykit/commit/a3ad43f8d536bd3be3ab7e18c7e022f5201c4987)), closes [#45](https://github.com/pkdee/daykit/issues/45)
* 알림(인앱) — Notification 도메인·API·NotifSheet 실연동 ([#48](https://github.com/pkdee/daykit/issues/48)) ([d6efacf](https://github.com/pkdee/daykit/commit/d6efacf2526737336b0c84376bd791b922e8d86d)), closes [#46](https://github.com/pkdee/daykit/issues/46)
* 외부 캘린더 동기화(Google/Notion, Apple 제약) ([#60](https://github.com/pkdee/daykit/issues/60)) ([45babe1](https://github.com/pkdee/daykit/commit/45babe1383c88f126bfce0a768e515555b02b580)), closes [#59](https://github.com/pkdee/daykit/issues/59)
* 인증 기동 보장 — JWT 임시 시크릿·소셜 미설정 시 수동 폴백 ([#52](https://github.com/pkdee/daykit/issues/52)) ([4842969](https://github.com/pkdee/daykit/commit/48429697922b7147c5bb30db2de38bfaf771ed44)), closes [#51](https://github.com/pkdee/daykit/issues/51)

## [1.0.0](https://github.com/pkdee/daykit/compare/server-v0.0.1...server-v1.0.0) (2026-06-03)


### Features

* **server:** 도메인 규칙·TimeDebt·자동 이월 구현 ([#17](https://github.com/pkdee/daykit/issues/17)) ([ad7565c](https://github.com/pkdee/daykit/commit/ad7565c4c17f3e229f624d7693f4172ea73496db)), closes [#12](https://github.com/pkdee/daykit/issues/12)
* **server:** 도메인·API 설계 문서화 및 Event/Task JPA 스켈레톤 ([#7](https://github.com/pkdee/daykit/issues/7)) ([2ec3a7c](https://github.com/pkdee/daykit/commit/2ec3a7c454323caa50c47b4bcc91c4eba25f7b6f)), closes [#6](https://github.com/pkdee/daykit/issues/6)
