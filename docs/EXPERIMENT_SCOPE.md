# 실험 범위 — EXP-001 등급·수수료 계산

- 작성일: 2026-10-12 · Phase 2
- 기준 명세: [BUSINESS_RULES.md](BUSINESS_RULES.md) 1.0-draft (커밋 `2c2b312`). 이 문서는 Phase 1 명세를 수정하지 않고, 이번 실험에서 다룰 **부분집합**만 정한다.
- 실험 기록: [`experiments/EXP-001-grade-fee-baseline/`](../experiments/EXP-001-grade-fee-baseline/)
- 최초 구현: 커밋 `60424d5`, 태그 `exp-001-ai-original`

## 1. 실험 목적

AI(Claude)가 작성한 같은 코드를 대상으로 두 가지 테스트 작성 방식을 비교한다.

| 방식 | 설명 |
|------|------|
| A | Claude가 직접 테스트 필요성을 판단하고 테스트를 작성한다 |
| B | Jev가 테스트 필요 점수를 산출하고, Claude가 그 결과를 참고해 테스트를 작성한다 |

두 방식의 테스트를 PIT 뮤테이션 테스트로 측정해 결함 탐지 능력을 비교한다. 정산 시스템을 완성하는 것이 목적이 아니라, 테스트 방법론을 경험하기 위한 작은 실험이다.

이번 Phase에서는 비교 대상이 되는 **최초 비즈니스 코드**만 만들었다. 테스트 작성, Jev 점수 산출, JaCoCo, PIT는 수행하지 않았다.

## 2. 구현 기능

1. 최근 12개 완료 월의 누적 사용금액 계산
2. 누적 금액에 따른 회원 등급 결정
3. 등급별 수수료율 결정
4. 수수료 계산 및 원 단위 반올림
5. (사용자 응답으로 추가) 정산월 마감 여부 검증 — 아래 §5 참고

## 3. 포함된 Rule ID

| Rule ID | 포함 정도 | 이번 실험에서의 의미 |
|---------|-----------|----------------------|
| BR-TIM-001 | 부분 | 월 경계를 Asia/Seoul로 판정. 입력이 `Instant`라서 "오프셋 없는 시각 거부(`INVALID_REQUEST`)"는 해당 없음 |
| BR-TIM-002 | 포함 | 월 구간 `[start(m), start(m+1))` |
| BR-TIM-004 | 포함 | `Clock` 주입 |
| BR-AMT-001 | 부분 | 금액은 `long` 정수. 소수 금액 거부는 입력 파싱 단계라 해당 없음 |
| BR-AMT-002 | 포함 | 결제 금액 1 ~ 1,000,000,000원 |
| BR-AMT-003 | 포함 | 64비트 정수, 합산 시 오버플로 검출 |
| BR-TXN-001 | 포함 | 결제는 귀속월 사용금액을 증가 |
| BR-PRF-001 | 부분 | 환불이 없으므로 월 순사용금액 = 결제 합계. 기준 시각(asOf) 개념은 실질적으로 쓰이지 않음 |
| BR-PRF-002 | 포함 | 직전 12개 완료 월 합계 |
| BR-PRF-003 | 포함 | 당월과 13개월 전 이전 거래 제외 |
| BR-PRF-004 | 포함 | 거래 없는 월은 0 |
| BR-GRD-001 | 포함 | 등급 구간과 요율 |
| BR-GRD-002 | 부분 | 등급 실적은 정산월 시작 전 거래로만 계산. 환불이 없으므로 시점 차이는 생기지 않음 |
| BR-GRD-004 | 포함 | 이력 없으면 BASIC |
| BR-FEE-001 | 포함 | 수수료 기준액 = 정산월 사용금액 (환불 없음) |
| BR-FEE-002 | 포함 | bp 정수, 원 단위 HALF_UP |
| BR-FEE-003 | 포함 | 월 합계에 1회 반올림 |
| BR-FEE-004 | 포함 | 기준액 0이면 0원 |
| BR-FEE-005 | 포함 | 수수료는 0 이상 |
| BR-STL-002 | 포함 | 정산월이 끝나기 전 계산 거부 (사용자 결정, §5) |
| BR-STL-011 | 부분 | 결과에 정산월, 산정 기간, 등급 실적, 등급, 기준액, bp, 수수료 포함. 정산 ID·회원 ID·확정 시각은 없음 |

## 4. 제외된 Rule ID

명세는 삭제하지 않고 이번 실험에서만 다루지 않는다.

| Rule ID | 제외 사유 |
|---------|-----------|
| BR-TIM-003 | 거래 등록 기능 없음 (DB 저장 제외) |
| BR-TXN-002 ~ BR-TXN-007 | 거래 중복 방지, 회원 확인, 거래 저장 제외 |
| BR-RFD-001 ~ BR-RFD-010 | 환불 제외 |
| BR-PRF-005 | 환불이 없으면 음수 순사용금액이 생기지 않음 |
| BR-GRD-003 | 확정 등급의 사후 변경은 환불이 있어야 생기는 문제 |
| BR-STL-001, BR-STL-003 ~ BR-STL-010, BR-STL-012 | 정산 확정·저장, 멱등성, 동시성, 재정산, 회원 확인, 문자열 정산월 파싱 제외 |

OPEN ID와 충돌 ID(C-01 ~ C-09)는 Phase 1 문서 그대로 유지한다. 이번 실험과 직접 관련된 것은 C-06(오버플로 방어 분기의 도달 가능성)과 C-08(Clock 주입과 기간 경계 테스트)이다.

## 5. 잠정 정책 (실험용 임시 전제)

> 아래 항목은 **사용자 승인으로 확정된 정책이 아니다.** 이번 실험을 진행하기 위한 임시 전제이며, `OPEN_QUESTIONS.md`의 상태(승인 대기)는 바뀌지 않는다.

| OPEN ID | 이번 실험의 전제 |
|---------|------------------|
| OPEN-01 | 시간대 Asia/Seoul |
| OPEN-03 | 결제 금액 1 ~ 1,000,000,000원 |
| OPEN-04 | 원 단위 HALF_UP, 월 합계에 1회 |
| OPEN-15 | 고정 등급표와 수수료율 (BASIC 3% / SILVER 2% / GOLD 1% / VIP 0.5%) |

추가로 Phase 2에서 사용자에게 질문해 정한 사항:

| 질문 | 사용자 응답 | 반영 |
|------|-------------|------|
| 주입한 Clock의 역할 | "M 지정 + 마감 검증" | 호출자가 정산월 `M`을 넘기고, `Clock`의 현재 시각이 `start(M+1)`보다 이전이면 거부 (BR-STL-002 포함) |

## 6. 실험 대상 클래스와 메서드

패키지: `com.testability.lab.billing`

| 클래스 | 메서드 / 구성 요소 | 역할 | 관련 규칙 |
|--------|--------------------|------|-----------|
| `Grade` (enum) | `BASIC`, `SILVER`, `GOLD`, `VIP` 상수 (하한, bp) | 등급표 | BR-GRD-001 |
| | `static Grade of(long gradeBasisAmount)` | 등급 실적 → 등급. 음수면 `IllegalArgumentException` | BR-GRD-001, BR-GRD-004 |
| | `long minimumAmount()`, `int rateBasisPoints()` | 하한, 요율(bp) | BR-GRD-001, BR-FEE-002 |
| `Payment` (record) | 생성자 `Payment(long amount, Instant occurredAt)` | 금액 범위 검증, 범위 밖이면 `IllegalArgumentException("INVALID_AMOUNT: …")` | BR-AMT-002 |
| `FeeResult` (record) | 필드 8개 (정산월, 산정 기간 시작·끝, 등급 실적, 등급, 기준액, bp, 수수료) | 계산 결과 | BR-STL-011(부분) |
| `FeeCalculator` | 생성자 `FeeCalculator(Clock clock)` | Clock 주입 | BR-TIM-004 |
| | `FeeResult calculate(List<Payment>, YearMonth)` | 마감 검증 → 누적 → 등급 → 기준액 → 수수료. 미마감이면 `IllegalStateException("SETTLEMENT_PERIOD_NOT_CLOSED: …")` | BR-STL-002, 전체 |
| | `long cumulativeUsage(List<Payment>, YearMonth)` | `M-12` ~ `M-1` 귀속 결제 합계 (`Math.addExact`) | BR-PRF-002, 003, BR-AMT-003 |
| | `long monthlyUsage(List<Payment>, YearMonth)` | 지정 월 귀속 결제 합계 | BR-FEE-001 |
| | `long calculateFee(long, Grade)` | `BigDecimal`로 `기준액 × bp ÷ 10000`, HALF_UP. 음수면 `IllegalArgumentException` | BR-FEE-002 ~ 005 |
| | `static YearMonth attributionMonth(Payment)` (package-private) | Asia/Seoul 기준 귀속월 | BR-TIM-001, 002 |
| | `static Instant startOf(YearMonth)` (package-private) | 월 시작 시각 | BR-TIM-002 |

구조 메모 (최초 구현의 판단을 그대로 기록):
- 계층, 인터페이스, 저장소, 스프링 빈 등록 없이 순수 Java 클래스 4개로 구성
- 결제 목록은 모두 한 회원의 것이라고 가정하며 회원 ID는 없음
- 오류는 별도 예외 타입 없이 JDK 예외(`IllegalArgumentException`, `IllegalStateException`)로 던지고, 오류 코드는 메시지 앞부분에 넣음. 합산 오버플로는 `Math.addExact`의 `ArithmeticException`이 그대로 전파되며, 명세의 `AMOUNT_OVERFLOW` 코드 문자열은 붙지 않음
- 정산월이 끝난 뒤의 거래나 미래 시각의 거래가 목록에 있어도 거부하지 않고, 귀속월 필터에 따라 집계에서 빠짐

## 7. 적용 가능한 테스트 케이스 (TEST_CASE_MATRIX 기준)

테스트 작성 시 참고용. 이번 Phase에서는 작성하지 않았다.

| 영역 | 적용 가능 | 적용 불가 (제외 기능) |
|------|-----------|------------------------|
| GRD | TC-GRD-01 ~ 09 | — |
| PRD | TC-PRD-01 ~ 10 | — |
| FEE | TC-FEE-01 ~ 17 | — |
| AMT | TC-AMT-01 ~ 06, 08 | TC-AMT-07 (소수 금액 파싱) |
| STL | TC-STL-04, 05 | 나머지 (확정·저장·멱등·API·회원) |
| TIM | TC-TIM-07 | TC-TIM-01 ~ 06 (거래 등록·입력 형식) |
| RFD, DUP, NEG, CON | — | 전체 |

매트릭스의 기대값 중 환불이 섞인 데이터는 이번 범위에서 쓰지 않는다. 매트릭스의 "등급 / GB / FB / 수수료"는 `FeeResult`의 `grade` / `gradeBasisAmount` / `feeBaseAmount` / `fee`에 대응한다.
