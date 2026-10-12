# AI Testability Lab

AI가 생성한 비즈니스 코드의 테스터빌리티를 실험하는 프로젝트입니다.
실험 대상: 최근 12개월 누적 사용금액 기반 회원 등급 산출 및 등급별 차등 수수료를 적용하는 월별 정산 API.

## 빌드 / 실행

사전 조건: 로컬에 JDK 21 설치 (Gradle 데몬과 컴파일 모두 Java 21 사용)

```bash
./gradlew clean build
./gradlew bootRun
```

## 문서

- [프로젝트 개요](docs/PROJECT_OVERVIEW.md)
- [실험 프로토콜](docs/EXPERIMENT_PROTOCOL.md)
- [AI 실험 로그](docs/AI_EXPERIMENT_LOG.md)
- [테스트 결과](docs/TEST_RESULTS.md)
- [의사결정 기록](docs/DECISIONS.md)
