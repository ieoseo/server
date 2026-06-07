package app.ieoseo.server.infrastructure.scheduling

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 스케줄링 활성화 (#55).
 *
 * 애플리케이션 전역에서 `@Scheduled` 잡(자정 부채 생성·반복 인스턴스 생성·D-Day/스트릭 알림)을
 * 동작시키기 위한 단일 진입점이다. `ServerApplication` 은 수정하지 않고 이 설정으로 분리한다.
 *
 * 크론 표현식은 각 잡 서비스에서 `ieoseo.schedule.*` 프로퍼티(`application.yaml`)로 주입한다.
 * 잡 로직 자체는 `@Service` 의 `run(today)` 메서드에 있고 `@Scheduled` 는 그 메서드를 호출만 한다
 * (테스트는 service 메서드를 직접 호출하므로 크론은 테스트 대상이 아니다).
 */
@Configuration
@EnableScheduling
class SchedulingConfig
