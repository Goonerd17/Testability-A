# 의사결정 기록 (Decision Log)

결정은 번호를 붙여 추가만 한다. 결정을 바꿀 때는 기존 항목을 지우지 않고 새 항목을 만들어 "D-NNN을 대체함"이라고 적는다.

양식: **상태**(제안/채택/대체됨) · **일자** · **맥락** · **결정** · **근거** · **결과/영향**

---

## D-001 Java 21 (Eclipse Temurin 21.0.12.1) 사용

- 상태: 채택 · 일자: 2026-10-12
- 맥락: 시스템 기본 `JAVA_HOME`은 JDK 26이며, `~/.jdks`에 Temurin 21, Microsoft 21 등이 설치되어 있다.
- 결정: 컴파일·실행 모두 Java 21을 사용한다. `build.gradle`에 `toolchain { languageVersion = 21 }`를 지정한다.
- 근거: 프로젝트 요구사항이 Java 21. LTS 버전. Spring Boot 3.5 지원 범위 내.
- 결과: 기본 `JAVA_HOME`이 26이어도 Gradle 툴체인이 Java 21로 컴파일한다.

## D-002 Spring Boot 3.5.16 사용

- 상태: 채택 · 일자: 2026-10-12
- 맥락: Maven Central 조회 결과 3.x 최신은 3.5.16, 전체 최신은 4.2.0-M2(마일스톤).
- 결정: 요구사항의 "Spring Boot 3.x" 중 최신 패치인 3.5.16을 사용한다.
- 근거: 요구사항이 3.x로 명시됨. Java 21 지원. BOM이 JUnit 5, AssertJ, Testcontainers, REST Assured, PostgreSQL JDBC 버전을 관리해 줌.
- TODO: Spring Boot 3.5 라인의 OSS 지원 종료 일정은 공식 사이트에서 확인하지 않았다. 발표 시 언급이 필요하면 확인 후 기록한다. 4.x로의 전환은 요구사항 변경 사항이므로 사용자 결정이 필요하다.

## D-003 Gradle 8.14.3 (Wrapper, Groovy DSL) 및 데몬 JVM 21 고정

- 상태: 채택 · 일자: 2026-10-12
- 맥락: 전역 Gradle 미설치, 로컬 캐시에 8.14.3 배포본 존재. Spring Boot 3.5는 Gradle 8.x를 지원한다.
- 결정:
  - Gradle Wrapper 8.14.3을 사용한다.
  - `gradle/gradle-daemon-jvm.properties`에 `toolchainVersion=21`을 지정해 Gradle 데몬도 Java 21로 실행한다.
  - 툴체인 자동 다운로드(foojay resolver)는 사용하지 않는다.
- 근거: 기본 `JAVA_HOME`이 JDK 26이어서, 개발자 환경에 따라 데몬 JVM이 달라질 수 있다. 측정 재현성을 위해 고정한다. 자동 다운로드는 빌드 시 외부 네트워크 의존을 늘리므로 제외.
- 결과: JDK 21이 로컬에 없는 환경에서는 빌드가 실패한다 → 사전 설치가 필요하다. `updateDaemonJvm` 태스크는 다운로드 저장소 설정 없이 실패하므로 속성 파일을 직접 작성했다(로그 참고).

## D-004 패키지명 `com.testability.lab`

- 상태: 채택 · 일자: 2026-10-12
- 맥락: 특정 회사 도메인을 사용하지 않는다.
- 결정: 루트 패키지를 `com.testability.lab`, 아티팩트명을 `testability-lab`으로 한다.
- TODO: 도메인 하위 패키지 구조(레이어형/기능형)는 아키텍처 규칙(ArchUnit)과 함께 이후 Phase에서 결정한다.

## D-005 Phase 0의 의존성은 최소화

- 상태: 채택 · 일자: 2026-10-12
- 맥락: 이번 Phase는 골격 생성과 기동 확인이 목적이며, 다음 Phase를 선행하지 않는다.
- 결정: `spring-boot-starter-web`, `spring-boot-starter-test`, `junit-platform-launcher`만 포함한다. PostgreSQL/JPA(또는 JDBC), JaCoCo, PIT, PMD, ArchUnit, Testcontainers, REST Assured는 해당 Phase에서 추가한다.
- 근거: DB 의존성을 지금 넣으면 DB 없이 애플리케이션이 기동되지 않는다(현재 Docker 데몬 미실행, psql 미설치). 또한 데이터 접근 기술(JPA vs JDBC)은 테스터빌리티 실험 설계와 연결되는 결정이므로 미리 정하지 않는다.

## D-006 실험 산출물 3중 보존 (디렉터리 스냅샷 + Git 태그 + 로그)

- 상태: 채택 · 일자: 2026-10-12
- 맥락: AI 최초 생성 코드와 이후 수정본을 비교해야 한다.
- 결정: `experiments/EXP-NNN-<slug>/`에 프롬프트·원본·리포트를 보존하고, `exp-NNN-ai-original` 등 Git 태그를 남기며, `AI_EXPERIMENT_LOG.md`에 append-only로 기록한다. 상세는 [EXPERIMENT_PROTOCOL.md](EXPERIMENT_PROTOCOL.md).
- 근거: Git 이력만으로는 프롬프트 원문과 리포트가 남지 않고, 디렉터리만으로는 diff가 불편하다. 두 방식을 함께 써서 발표 자료 작성 시 어느 쪽으로도 추적할 수 있게 한다.

## D-007 IDE 설정(`.idea/`)은 버전 관리 제외

- 상태: 채택 · 일자: 2026-10-12
- 결정: `.gitignore`에 `.idea/`, `*.iml`을 추가한다.
- 근거: 개인 환경 설정이며 실험 재현에 필요하지 않다.

## D-008 줄바꿈 정규화 (`.gitattributes`)

- 상태: 채택 · 일자: 2026-10-12
- 결정: 텍스트 파일은 LF, `*.bat`/`*.cmd`는 CRLF, `*.jar`는 바이너리로 지정한다.
- 근거: Windows 환경에서 생성하지만 `gradlew` 스크립트가 다른 OS에서도 동작해야 하고, diff 비교 시 줄바꿈 차이로 인한 노이즈를 없애기 위함.
