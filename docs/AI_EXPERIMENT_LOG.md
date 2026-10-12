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

---

## [2026-10-12] Phase 1 — 비즈니스 명세 작성

- 수행 주체: AI(Claude Opus 5.5, Claude Code CLI) — 사용자 지시에 따라 수행
- 목적: 구현 전에 업무 규칙, 도메인 모델, 테스트 기대값을 확정한다. 코드는 작성하지 않음.
- 입력: 사용자가 제시한 등급 정책(BASIC/SILVER/GOLD/VIP 구간·요율)과 필수 업무 규칙 12개

### 1. 산출물

| 파일 | 내용 |
|------|------|
| `docs/BUSINESS_RULES.md` (신규) | Rule ID 50개 (TIM 4, AMT 3, TXN 7, RFD 10, PRF 5, GRD 4, FEE 5, STL 12), 오류 코드, 요구사항 추적표 |
| `docs/DOMAIN_MODEL.md` (신규) | 값 객체, 엔티티, 불변식, 계산 흐름, 포트(Fake 대상), 영속화 제약, API 초안 |
| `docs/TEST_CASE_MATRIX.md` (신규) | 경계값 분석 6개 영역, 테스트 케이스 97개 (GRD 9, PRD 10, RFD 16, FEE 17, AMT 8, DUP 8, TIM 7, STL 11, NEG 4, CON 7), 규칙→테스트 역추적표 |
| `docs/OPEN_QUESTIONS.md` (신규) | 잠정 결정 16개(OPEN-01~16), 규칙 간 충돌 9개(C-01~09) |
| `docs/PROJECT_OVERVIEW.md` (수정) | Phase 0 질문 Q1~Q10과 규칙 연결, 문서 목록, Phase 현황 |
| `docs/DECISIONS.md` (수정) | D-009 ~ D-011 추가 |

위 개수는 다음 명령으로 센 값이다.
`grep -oE '^\| BR-[A-Z]+-[0-9]{3}' BUSINESS_RULES.md | sort -u | wc -l` → 50
`grep -oE '^\| TC-[A-Z]+-[0-9]+' TEST_CASE_MATRIX.md | sort -u | wc -l` → 97

### 2. 명시적으로 정한 핵심 정책 (지시: 모호하게 두지 말 것)

- 환불의 과거 등급 영향: 확정 등급은 불변. 환불은 `refundedAt` 기준으로 인식되어 이후 정산월 등급에만 반영 (BR-RFD-008, 009)
- 음수 순사용금액: 정상 경로에서 불가능한 불변식. 발생 시 `NEGATIVE_NET_USAGE` 오류, 0 보정·상계 금지 (BR-PRF-005)
- 수수료 반올림: bp 정수 계산, 원 단위 HALF_UP, 월 합계에 1회 (BR-FEE-002, 003)
- 정산 재실행: 즉시 확정, 재요청은 최초 결과 반환, 수정·재계산 없음, 정산 후 환불은 반영·환급 없음 (BR-STL-004 ~ 007)

### 3. 검증

- 문서 간 참조 검사: 매트릭스·도메인 모델·OPEN 문서가 참조한 Rule ID가 모두 `BUSINESS_RULES.md`에 정의되어 있음을 셸 스크립트로 확인. 모든 Rule ID가 역추적표에 1행 이상 존재함을 확인.
- 테스트 기대값은 손 계산값이다. 코드로 검증하지 않았다(코드 없음).
- 빌드/테스트 실행: 해당 없음 (문서만 변경)

### 4. 남은 질문/TODO

- OPEN-01 ~ 16 사용자 승인 대기
- 규칙→테스트 역추적표는 ID 존재 여부만 기계적으로 확인했고, 범위 표기(`01 ~ 08`)의 개별 항목까지는 확인하지 않음

---

## [2026-10-12] Phase 2 / EXP-001 — 실험 범위 확정 및 최초 비즈니스 코드 구현

- 수행 주체: AI(Claude Opus 5.5, Claude Code CLI) — 사용자 지시에 따라 수행
- 목적: A(Claude 단독)·B(Jev 점수 참고) 테스트 방식 비교의 대상이 될 최소 비즈니스 코드 작성. 테스트, Jev, JaCoCo, PIT는 수행하지 않음.

### 1. 확인
- `git status`: 작업 트리 깨끗함. Phase 1 문서는 커밋 `2c2b312`에 있음
- `docs/` 확인: BUSINESS_RULES, DOMAIN_MODEL, TEST_CASE_MATRIX, DECISIONS, OPEN_QUESTIONS 존재

### 2. 사용자 질의
- 질문: 주입한 Clock의 역할 (BR-STL-002가 포함 목록에 없었음)
- 응답: "M 지정 + 마감 검증 (Recommended)" → BR-STL-002 포함
- 원문: `experiments/EXP-001-grade-fee-baseline/prompt.md` Turn 2

### 3. 실행 명령어와 결과

| 명령어 | 결과 |
|--------|------|
| `git commit` (prompt.md, README.md) | `7856f7a exp(001): prompt` |
| `./gradlew clean compileJava` | `BUILD SUCCESSFUL in 1s` — **첫 컴파일 성공, 수정 없음** |
| `diff -r src/.../billing experiments/.../ai-original/...` | 차이 없음 (원본 복사 확인) |
| `./gradlew build` | `BUILD SUCCESSFUL`, `:test NO-SOURCE` |
| `git commit` (구현 4개 파일 + ai-original 복사본) | `60424d5 exp(001): ai-original` |
| `git tag -a exp-001-ai-original` | 생성 |

### 4. 산출물
- `src/main/java/com/testability/lab/billing/`: `Grade.java`, `Payment.java`, `FeeResult.java`, `FeeCalculator.java`
- `docs/EXPERIMENT_SCOPE.md`: 목적, 포함/제외 Rule ID, 잠정 정책(임시 전제), 대상 클래스·메서드, 적용 가능 TC
- `experiments/EXP-001-grade-fee-baseline/`: prompt.md, README.md, ai-original/, result.md

### 5. 수정 이력
- 없음. 최초 구현을 그대로 보존

### 6. 남은 질문/TODO
- OPEN-01, 03, 04, 15는 이번 실험의 임시 전제로만 사용. 승인 상태는 그대로 "승인 대기"
- 이번 Phase의 Clock 역할 결정은 `DECISIONS.md`에 추가하지 않았다 (Phase 1 문서 수정 금지 지시). EXPERIMENT_SCOPE §5와 이 로그에만 기록
- Jev가 무엇이고 어떻게 실행하는지는 아직 확인하지 않음 → 방식 B Phase 전에 필요

---

## [2026-10-12] Phase 3 / EXP-001-A — Claude 단독 테스트

- 수행 주체: AI(Claude Opus 5.5, Claude Code CLI)
- 브랜치: `main`(c4dc2b7)에서 `exp-001-a` 생성. `main`은 변경하지 않음

### 실행 명령어와 결과

| 명령어 | 결과 |
|--------|------|
| `git switch -c exp-001-a` | 브랜치 생성 |
| (실수) 같은 셸 명령에 불필요한 `python -` 호출을 넣어 명령이 멈춤 | 300초 타임아웃 후 백그라운드 작업 중지. `build.gradle`은 바뀌지 않은 상태였음. 이후 Edit 도구로 다시 작성 |
| `curl .../org.jacoco.agent/maven-metadata.xml` | 최신 0.8.15 |
| `./gradlew build` (JaCoCo 추가 후) | `BUILD SUCCESSFUL`, `jacocoTestReport SKIPPED` (테스트 없음) |
| `git commit` build.gradle | `6be3c5c build: JaCoCo 0.8.15 커버리지 측정 설정` (실험 B에 cherry-pick 가능하도록 분리) |
| `git commit` approach-A/prompt.md | `05286a3` |
| `./gradlew clean test` | `BUILD SUCCESSFUL in 10s`, 65건 성공 / 0 실패 |
| `git commit` 테스트 3개 파일 | `eeac75d`, 태그 `exp-001-a-tests` |
| `git diff exp-001-ai-original --stat -- src/main` | 출력 없음 (비즈니스 코드 무변경 확인) |

### 결과 요약
- 테스트 메서드 30개 / 실행 65건, 전부 성공. 발견한 버그 없음
- JaCoCo billing 패키지: Line 66/67 (98.5%), Branch 26/26 (100%). 미커버: `Grade.minimumAmount()`
- 상세: `experiments/EXP-001-grade-fee-baseline/approach-A/result.md`

### 수정 이력
- 비즈니스 코드: 없음
- 테스트: 최초 작성 후 수정 없음 (첫 실행에서 전부 통과)
