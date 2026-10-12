# AI 실험 로그

실행한 명령어, 결과, 실패 원인, 수정 이력을 시간 순으로 기록한다. 이 문서는 추가만 하며(append-only), 과거 항목은 수정하지 않는다.

## 기록 양식

```
## [YYYY-MM-DD] <Phase 또는 EXP-ID> — <제목>
- 수행 주체: 사람 / AI(<모델명>, <도구>)
- 목적:
- 실행 명령어와 결과:
- 실패와 원인:
- 수정 이력:
- 남은 질문/TODO:
```

---

## [2026-10-12] Phase 0 — 개발 환경 확인 및 프로젝트 골격 생성

- 수행 주체: AI(Claude Opus 5.5, Claude Code CLI) — 사용자 지시에 따라 수행
- 목적: Java 21 / Spring Boot 3.x / Gradle 기반 최소 프로젝트 구조와 문서 체계 구성. 비즈니스 로직과 테스트는 구현하지 않음.

### 1. 환경 확인

| 명령어 | 결과 |
|--------|------|
| `java -version` | `openjdk version "26.0.2.1"` — 시스템 기본 `JAVA_HOME`이 JDK 26 |
| `ls ~/.jdks/` | `ms-21.0.12.1`, `openjdk-26.0.2.1`, `temurin-1.8.0_504`, `temurin-11.0.32.1`, `temurin-21.0.12.1` |
| `gradle --version` | `command not found` — 전역 Gradle 미설치 |
| `ls ~/.gradle/wrapper/dists` | `gradle-8.14.3-bin` 캐시 존재 |
| `docker --version` | `Docker version 29.7.2` (CLI 설치됨) |
| `docker info` | 데몬 연결 실패 (`dockerDesktopLinuxEngine` 파이프 없음) → Docker Desktop 미실행 |
| `psql --version` | `command not found` |
| `k6 version` | `command not found` |

### 2. 버전 조회

- `curl https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/maven-metadata.xml` → 3.x 최신 `3.5.16`, 전체 최신 `4.2.0-M2`
- `io.spring.dependency-management` 최신 릴리스 → `1.1.7`
- Spring Boot 3.5.16 BOM 관리 버전 (`spring-boot-dependencies-3.5.16.pom`):
  JUnit Jupiter 5.12.2, AssertJ 3.27.7, PostgreSQL JDBC 42.7.11, Testcontainers 1.21.4, REST Assured 5.5.7

### 3. 프로젝트 생성

- `build.gradle`, `settings.gradle`, `TestabilityLabApplication.java`, `application.yml`, `.gitignore`, `.gitattributes` 작성
- Gradle Wrapper 생성 (캐시된 Gradle 8.14.3 배포본 사용, `JAVA_HOME=temurin-21.0.12.1`):
  `~/.gradle/wrapper/dists/gradle-8.14.3-bin/<hash>/gradle-8.14.3/bin/gradle wrapper --gradle-version 8.14.3 --distribution-type bin`
  → `BUILD SUCCESSFUL in 40s`

### 4. 빌드 검증

| 명령어 | 조건 | 결과 |
|--------|------|------|
| `./gradlew --version` | `JAVA_HOME`=JDK 21 | Gradle 8.14.3, Launcher JVM 21.0.12.1 |
| `./gradlew clean build` | `JAVA_HOME`=JDK 21 | `BUILD SUCCESSFUL in 17s` (`compileTestJava NO-SOURCE`, `test NO-SOURCE`) |
| `./gradlew clean build` | `JAVA_HOME`=JDK 26 (기본값) | `BUILD SUCCESSFUL in 7s` — 데몬이 JDK 26으로 실행됨 |
| `./gradlew updateDaemonJvm --jvm-version=21` | — | **실패** (아래 참고) |
| `./gradlew clean build` | `gradle-daemon-jvm.properties` 수동 작성 후, `JAVA_HOME`=JDK 26 | `BUILD SUCCESSFUL in 7s`, `Daemon JVM: Compatible with Java 21 ... (from gradle/gradle-daemon-jvm.properties)` |

### 5. 실패와 원인

- **`updateDaemonJvm` 실패**
  - 오류: `Error while evaluating property 'toolchainDownloadUrls' of task ':updateDaemonJvm'` / `Invalid task configuration`
  - 원인: Gradle 8.14의 `updateDaemonJvm`은 툴체인 다운로드 저장소(예: foojay resolver 플러그인) 설정을 요구함. 이 프로젝트에는 설정하지 않음.
  - 조치: `gradle/gradle-daemon-jvm.properties`에 `toolchainVersion=21`만 직접 작성. 로컬에 설치된 JDK 21을 자동 탐지해 사용함을 `./gradlew --version`으로 확인. (자동 다운로드는 사용하지 않음 — DECISIONS D-003 참고)

### 6. 애플리케이션 기동 검증

- 명령어: `~/.jdks/temurin-21.0.12.1/bin/java -jar build/libs/testability-lab-0.0.1-SNAPSHOT.jar` (백그라운드 실행 후 로그 확인, 종료)
- 로그:
  - `Starting TestabilityLabApplication v0.0.1-SNAPSHOT using Java 21.0.12.1`
  - `Tomcat started on port 8080 (http) with context path '/'`
  - `Started TestabilityLabApplication in 1.523 seconds (process running for 1.901)`
- `curl http://localhost:8080/` → HTTP `404` (등록된 엔드포인트가 없으므로 예상된 결과, 서버 응답 확인 목적)
- 종료 후 8080 포트 LISTEN 없음 확인

### 7. 남은 질문/TODO

- 비즈니스 명세 미확정 항목 Q1~Q10 → [PROJECT_OVERVIEW.md §2.2](PROJECT_OVERVIEW.md)
- Docker Desktop 미실행 → Testcontainers 사용 전 실행 필요
- k6 미설치 → 성능 측정 Phase 전 설치 필요
- 이번 Phase 산출물은 아직 커밋하지 않음 (사용자 확인 후 커밋)
