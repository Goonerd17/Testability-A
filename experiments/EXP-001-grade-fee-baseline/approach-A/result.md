# 결과 — EXP-001 실험 A (Claude 단독 테스트)

- 일시: 2026-10-12
- 모델 / 도구: Claude Opus 5.5 (`claude-opus-5-5`) / Claude Code CLI
- 브랜치: `exp-001-a`
- 대상 코드: `exp-001-ai-original` (커밋 `60424d5`). 실험 중 비즈니스 코드 변경 없음 (`git diff exp-001-ai-original -- src/main` 결과 비어 있음)
- 테스트 커밋: `eeac75d`, 태그 `exp-001-a-tests`
- 제약 준수: Jev 미사용, 테스트 필요 점수 미산출

## 1. 테스트 대상 선정과 이유

| 대상 | 선정 | 이유 |
|------|------|------|
| `FeeCalculator.calculate` | 직접 | 마감 검증, 12개월 기간 경계, 시간대, 등급, 수수료가 모두 모이는 진입점. 기간 경계(off-by-one), 시간대 변환, `isBefore` 경계처럼 틀리기 쉬운 비교가 집중됨. 명세(TEST_CASE_MATRIX)의 시나리오가 정산 결과로 정의되어 있음 |
| `FeeCalculator.calculateFee` | 직접 + 간접 | 반올림·bp 나눗셈은 1원 단위 오차가 생기기 쉬움. 음수 입력을 0으로 보정하지 않는지 확인 필요 |
| `Grade.of` | 직접 | 이상/초과 혼동이 잦은 경계 비교 4개 |
| `Payment` 생성자 | 직접 | 금액 범위 검증이 이곳에만 있음 |
| `FeeCalculator.cumulativeUsage` | 간접 (`calculate`) | 명세 시나리오가 정산 결과로 정의됨. 단독 테스트는 중복 |
| `FeeCalculator.monthlyUsage` | 간접 | 위와 같음 |
| `FeeCalculator.attributionMonth`, `startOf` | 간접 | package-private 보조 메서드. 시간대·경계 테스트로 검증 |
| `FeeResult` | 선정 안 함 | 로직 없는 record |
| `Grade.minimumAmount()` | 선정 안 함 | 로직 없는 접근자. 외부 호출처도 없음 |

## 2. 테스트 수

| 클래스 | 테스트 메서드 | 실행 건수 (파라미터 포함) |
|--------|---------------|---------------------------|
| `GradeTest` | 3 | 15 |
| `PaymentTest` | 2 | 6 |
| `FeeCalculatorTest` | 25 | 44 |
| **합계** | **30** | **65** |

메서드 수는 `@Test`·`@ParameterizedTest` 개수, 실행 건수는 `build/test-results/test/*.xml`의 `tests` 합계다.

## 3. 테스트 결과

- 명령어: `./gradlew clean test` (`jacocoTestReport` 자동 실행)
- 결과: `BUILD SUCCESSFUL in 10s` · **65건 성공, 0건 실패, 0건 오류, 0건 스킵**
- 원본: `reports/test-results/*.xml`

## 4. 검증한 업무 규칙

| Rule ID | 테스트 |
|---------|--------|
| BR-GRD-001 | `GradeTest.gradeByBoundary`, `rateBasisPoints`, `negativeGradeBasisAmountIsRejected`, TC-GRD-02~08 |
| BR-GRD-002 (부분) | TC-PRD-03, 04 (정산월 시작 전 거래만 등급에 반영) |
| BR-GRD-004 | TC-GRD-01 |
| BR-PRF-002 | TC-GRD-09, PRD-01, 03, 05, 07, 08 |
| BR-PRF-003 | TC-PRD-02, 04, 05 |
| BR-PRF-004 | TC-GRD-01, TC-STL-04(b) |
| BR-TXN-001 | 결제 합산 전반 |
| BR-TIM-001 | TC-PRD-06 |
| BR-TIM-002 | TC-PRD-01~04, 07, 09 |
| BR-TIM-004 | 모든 `FeeCalculatorTest` (고정 Clock), TC-TIM-07 |
| BR-AMT-002 | `PaymentTest` (TC-AMT-01~06) |
| BR-AMT-003 | TC-GRD-08, TC-AMT-08, TC-FEE-15~17 |
| BR-FEE-001 | TC-PRD-04, 06, FEE 전체 |
| BR-FEE-002 | TC-FEE-01~11, 15 |
| BR-FEE-003 | TC-FEE-13, 14 |
| BR-FEE-004 | TC-FEE-12, TC-STL-04(b) |
| BR-FEE-005 | `negativeFeeBaseIsRejected` |
| BR-STL-002 | TC-STL-04, 05 |
| BR-STL-011 (부분) | TC-PRD-10 (`FeeResult` 전체 필드 비교) |

EXPERIMENT_SCOPE.md §7에서 적용 가능하다고 정한 TC 46개(GRD 9, PRD 10, FEE 17, AMT 7, STL 2, TIM 1)를 모두 작성했다. 기대값은 TEST_CASE_MATRIX.md의 값을 그대로 사용했다.

## 5. 발견한 실제 버그

**없음.** 명세의 기대값과 다른 결과를 낸 테스트가 없었다.

이것이 "버그가 없다"는 증명은 아니다. 테스트 작성자(Claude)와 구현 작성자(Claude)가 같고 같은 명세를 봤으므로, 명세 해석을 똑같이 잘못했다면 테스트로 드러나지 않는다. 이 점은 PIT 측정과 실험 B 비교에서 확인할 대상이다.

## 6. 커버리지 (JaCoCo 0.8.15)

원본: `reports/jacoco/` (`jacocoTestReport.csv`, `.xml`, `html/`)

| 범위 | Line | Branch |
|------|------|--------|
| `com.testability.lab.billing` (실험 대상) | **66 / 67 (98.5%)** | **26 / 26 (100%)** |
| 프로젝트 전체 | 66 / 70 (94.3%) | 26 / 26 (100%) |

클래스별 (CSV 원본 값):

| 클래스 | Line 미커버/커버 | Branch 미커버/커버 |
|--------|------------------|--------------------|
| `FeeCalculator` | 0 / 41 | 0 / 14 |
| `Grade` | 1 / 19 | 0 / 8 |
| `Payment` | 0 / 5 | 0 / 4 |
| `FeeResult` | 0 / 1 | 0 / 0 |
| `TestabilityLabApplication` (실험 대상 아님) | 3 / 0 | 0 / 0 |

미커버 라인: `Grade.java:23` `return minimumAmount;` (`minimumAmount()` 접근자. 테스트에서도, 운영 코드에서도 호출하지 않음).

## 7. 작성하지 않은 테스트와 이유

| 대상 | 이유 |
|------|------|
| 합산 오버플로 → 오류 (BR-AMT-003의 오류 경로) | 결제 상한 10억 원과 `long` 때문에 정상 입력으로는 `Math.addExact` 오버플로에 도달할 수 없음(약 92억 건 필요). OPEN_QUESTIONS C-06에서 예상한 그대로 |
| null 입력 (`payments`, `settlementMonth`, `clock`, `occurredAt`) | 명세에 null 처리 규칙이 없음. 구현의 `NullPointerException`을 기대값으로 삼으면 명세에 없는 기대값을 만드는 것이 됨 |
| 오류 코드와 예외 타입의 정확한 일치 | 명세는 오류 코드만 정의하고 예외 타입은 정하지 않음. 코드가 메시지에 있는 `INVALID_AMOUNT`, `SETTLEMENT_PERIOD_NOT_CLOSED`는 메시지로 확인했고, 코드가 정의되지 않은 음수 입력(`Grade.of`, `calculateFee`)은 "예외 발생"만 확인 |
| TC-AMT-07 (소수 금액) | 입력 타입이 `long`이라 해당 없음 (EXPERIMENT_SCOPE §7) |
| `cumulativeUsage`, `monthlyUsage` 단독 테스트 | `calculate` 테스트의 `gradeBasisAmount`, `feeBaseAmount`로 같은 값을 검증 |
| `FeeResult`, `Grade.minimumAmount()` | 로직 없음 |
| 기간 밖·미래 거래 거부 | BR-TIM-003은 실험 범위 밖. 구현은 거부하지 않고 집계에서 제외하며, 그 결과는 TC-PRD-02, 04 등으로 간접 확인 |
| 환불, 중복, 멱등성, 동시성, DB, API (RFD, DUP, NEG, CON, STL 대부분, TIM-01~06) | 실험 범위 밖 |

## 8. 관찰 (발표 메모)

- 명세 단계에서 TC와 기대값을 미리 정해 두었기 때문에, "무엇을 테스트할지"의 판단이 대부분 매트릭스를 옮기는 작업이 되었다. 실험 A의 선정 판단은 명세의 영향을 크게 받는다.
- Clock 주입 덕분에 마감 경계(`23:59:59.999999999` / `00:00:00`)를 나노초 단위로 테스트할 수 있었다 (C-08).
- Line/Branch 커버리지가 높아도 Mutation Score가 높다는 보장은 없다. 예를 들어 `>=`를 `>`로 바꾸는 변이는 경계값 테스트가 잡겠지만, 메시지에만 의존하는 오류 검증은 약할 수 있다. PIT 단계에서 확인한다.
