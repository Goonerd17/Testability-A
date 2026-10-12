# 테스트 결과

실제 도구로 측정한 값만 기록한다. 측정하지 않은 항목은 `미측정`, 측정이 실패한 항목은 `측정 실패(원인)`으로 적는다. 원본 리포트는 `experiments/EXP-NNN-*/reports/`에 보존한다.

## 기록 양식

| 항목 | 값 | 측정 명령어 | 측정 일시 | 커밋 |
|------|----|-------------|-----------|------|
| 테스트 수 (성공/실패/스킵) | | `./gradlew test` | | |
| Line Coverage | | `./gradlew jacocoTestReport` | | |
| Branch Coverage | | `./gradlew jacocoTestReport` | | |
| Mutation Score | | `./gradlew pitest` | | |
| PMD 위반 수 | | `./gradlew pmdMain` | | |
| 순환 복잡도 (최대/평균) | | (도구 결정 후 기재) | | |
| ArchUnit 규칙 위반 | | `./gradlew test` | | |
| k6 p95 응답시간 / 처리량 | | `k6 run ...` | | |

---

## Phase 0 (2026-10-12) — 프로젝트 골격

비즈니스 코드와 테스트가 없으므로 품질 지표는 측정하지 않았다.

| 항목 | 값 | 측정 명령어 | 비고 |
|------|----|-------------|------|
| 빌드 | 성공 | `./gradlew clean build` | `BUILD SUCCESSFUL` |
| 테스트 | 실행 대상 없음 | `./gradlew clean build` | `:test NO-SOURCE` |
| 애플리케이션 기동 | 성공 | `java -jar build/libs/testability-lab-0.0.1-SNAPSHOT.jar` | `Started TestabilityLabApplication in 1.523 seconds` (1회 측정, 참고용) |
| Line/Branch Coverage | 미측정 | — | JaCoCo 미도입 |
| Mutation Score | 미측정 | — | PIT 미도입 |
| PMD 위반 | 미측정 | — | PMD 미도입 |
| ArchUnit | 미측정 | — | ArchUnit 미도입 |
| 성능 | 미측정 | — | k6 미설치 |
