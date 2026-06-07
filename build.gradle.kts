plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.2.21"
}

group = "app.ieoseo"
version = "0.0.1-SNAPSHOT"
description = "server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    // JWT(access/refresh) 서명·검증. api 는 컴파일, impl/jackson 은 런타임.
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    // 관측성(Sentry) 코어 SDK. auto-config 스타터 대신 코어를 직접 init(ADR-0011, SB4 호환).
    implementation("io.sentry:sentry:8.16.0")
    // DB 마이그레이션(Flyway, ADR-0007). 부팅 시 db/migration 의 V1~V5 를 PostgreSQL 에 적용한다.
    // (ddl-auto: validate 전제) 버전은 Spring Boot BOM 이 관리한다.
    // Spring Boot 4 는 자동설정이 모듈로 분리됨 → spring-boot-flyway 가 있어야 부팅 시 자동 마이그레이션이 동작한다.
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    // Spring Security MockMvc 지원(인증/미인증 시나리오 슬라이스 테스트).
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // @DataJpaTest 슬라이스용 임베디드 DB(실 PostgreSQL 미연결 환경에서 Repository 쿼리 검증).
    testRuntimeOnly("com.h2database:h2")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 컨테이너 빌드 시 실행 가능한 bootJar 하나만 남기도록 plain jar 비활성(Dockerfile COPY 모호성 제거).
tasks.named<Jar>("jar") {
    enabled = false
}
