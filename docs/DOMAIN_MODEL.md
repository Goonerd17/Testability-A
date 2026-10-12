# 도메인 모델 — Membership Billing Lab

- 버전: 1.0-draft · 작성일: 2026-10-12 · Phase 1
- 이 문서는 **개념 모델**이다. 클래스 이름이나 패키지 구조를 강제하지 않는다. AI가 코드를 생성하는 실험에서는 이 개념과 불변식을 지키는지를 검증한다.
- 규칙의 근거는 [BUSINESS_RULES.md](BUSINESS_RULES.md)의 Rule ID로 표시한다.

## 1. 개념 지도

```
Member (1) ──< (N) Transaction
                    ├─ Payment  (1) ──< (0..N) Refund   [원거래 연결: BR-RFD-001]
                    └─ Refund

Member (1) ──< (N) Settlement      [(memberId, settlementMonth) 유일: BR-STL-004]

Settlement ──uses──> GradePolicy   [등급 구간·bp: BR-GRD-001]
           ──uses──> Clock         [현재 시각: BR-TIM-004]
```

## 2. 값 객체

| 이름 | 설명 | 불변식 | 관련 규칙 |
|------|------|--------|-----------|
| `Money` (원) | KRW 원 단위 정수 금액 | 64비트 이상 정수. 연산 결과가 범위를 넘으면 오류 | BR-AMT-001, BR-AMT-003 |
| `TransactionAmount` | 거래 1건 금액 | `1 ≤ 값 ≤ 1,000,000,000` | BR-AMT-002 |
| `BillingMonth` | 정산월/귀속월 (`YYYY-MM`) | 유효한 연월. `start()`, `next()`, `minusMonths(n)` 연산 | BR-TIM-001, BR-TIM-002, BR-STL-010 |
| `PerformancePeriod` | 등급 산정 기간 | `from = M-12`, `to = M-1` (12개월, 양끝 포함) | BR-PRF-002, BR-PRF-003 |
| `Grade` | BASIC / SILVER / GOLD / VIP | 하한·bp를 가짐 (표: BR-GRD-001) | BR-GRD-001 |
| `FeeRate` | 수수료율 (bp 정수) | 300 / 200 / 100 / 50. 부동소수점 사용 금지 | BR-FEE-002 |
| `TransactionId` | 요청자가 부여한 거래 식별자 | 결제·환불 통틀어 전역 유일 | BR-TXN-002 |

## 3. 엔티티와 애그리거트

### 3.1 Member
| 속성 | 설명 |
|------|------|
| `memberId` | 회원 식별자 |

- 이번 Lab에서 회원은 식별자만 가진다. 가입일, 탈퇴, 상태는 다루지 않는다(OPEN-07).

### 3.2 Payment (결제)
| 속성 | 설명 |
|------|------|
| `transactionId` | 전역 유일 ID |
| `memberId` | 회원 |
| `amount` | `TransactionAmount` |
| `occurredAt` | 발생 시각 (오프셋 포함 시각, Instant로 저장) |
| `attributionMonth` | `occurredAt`의 Asia/Seoul 기준 연월 (파생값) |
| `refundedTotal` | 누적 환불액 (파생값 또는 저장값) |

불변식
- `0 ≤ refundedTotal ≤ amount` (BR-RFD-004)
- 생성 후 `amount`, `occurredAt`, `memberId`는 바뀌지 않는다 (BR-TXN-007)
- 남은 환불 가능액 = `amount − refundedTotal`

### 3.3 Refund (환불)
| 속성 | 설명 |
|------|------|
| `transactionId` | 전역 유일 ID |
| `memberId` | 회원 (원거래의 회원과 같아야 함) |
| `originalTransactionId` | 원결제 ID |
| `amount` | `TransactionAmount` |
| `refundedAt` | 환불 인식 시각 |
| `attributionMonth` | **원결제의** `attributionMonth` (BR-RFD-002) |

불변식
- 원거래는 같은 회원의 Payment (BR-RFD-001, BR-RFD-005)
- `refundedAt ≥ 원거래.occurredAt` (BR-RFD-006)

**동시성 경계**: "누적 환불액 ≤ 원결제 금액"은 **Payment 하나를 단위로** 지켜야 한다. 같은 결제에 대한 환불 등록은 직렬화(비관적 잠금 또는 버전 기반 낙관적 잠금)해야 한다 (TC-CON-02, TC-CON-03).

### 3.4 Settlement (확정 정산)
| 속성 | 설명 | 규칙 |
|------|------|------|
| `settlementId` | 정산 ID | BR-STL-011 |
| `memberId` | 회원 | |
| `settlementMonth` | 정산월 `M` | |
| `grade` | 확정 등급 | BR-GRD-002 |
| `gradeBasisAmount` | `GB(M)` | BR-PRF-002 |
| `periodFrom`, `periodTo` | `M-12`, `M-1` | BR-PRF-002 |
| `feeBaseAmount` | `FB(M)` | BR-FEE-001 |
| `rateBp` | 적용 bp | BR-FEE-002 |
| `fee` | 수수료 (원) | BR-FEE-002, BR-FEE-003 |
| `confirmedAt` | 최초 확정 시각 | BR-STL-004 |

불변식
- 상태는 CONFIRMED 하나뿐이며 생성 후 바뀌지 않는다 (BR-STL-001, BR-STL-006)
- `(memberId, settlementMonth)`당 최대 1건 (BR-STL-004, BR-STL-005)
- `gradeBasisAmount ≥ 0`, `feeBaseAmount ≥ 0`, `fee ≥ 0` (BR-PRF-005, BR-FEE-005)
- `grade`는 `GradePolicy(gradeBasisAmount)`와 일치하고, `fee`는 `round_half_up(feeBaseAmount × rateBp / 10000)`와 일치

## 4. 도메인 서비스 (계산 규칙)

이름은 개념상 구분이며 구현 형태는 강제하지 않는다.

| 서비스 | 입력 → 출력 | 정의 |
|--------|-------------|------|
| `NetUsageCalculator` | (회원, 월 `m`, `asOf`) → `NU` | BR-PRF-001. 결과 < 0이면 `NEGATIVE_NET_USAGE` |
| `GradePolicy` | `GB` → `Grade` | BR-GRD-001 표. `GB < 0` 입력은 허용하지 않음 |
| `GradeDeterminer` | (회원, `M`) → (`Grade`, `GB`) | `GB = Σ NU(m, start(M))`, `m ∈ [M-12, M-1]` |
| `FeeCalculator` | (`FB`, `Grade`) → 수수료 | `round_half_up(FB × bp / 10000)` |
| `SettlementService` | (회원, `M`, 현재 시각) → `Settlement` | 아래 흐름 |

### 4.1 정산 흐름 (`SettlementService`)

1. `M` 형식 검증 → 실패 시 `INVALID_SETTLEMENT_MONTH` (BR-STL-010)
2. 회원 존재 확인 → `MEMBER_NOT_FOUND` (BR-STL-012)
3. `현재 시각 < start(M+1)` 이면 `SETTLEMENT_PERIOD_NOT_CLOSED` (BR-STL-002)
4. 기존 정산이 있으면 그대로 반환 (BR-STL-004)
5. `GB(M)`, `Grade` 계산 (asOf = `start(M)`)
6. `FB(M)` 계산 (asOf = `start(M+1)`)
7. 수수료 계산 (BR-FEE-002, BR-FEE-003)
8. 저장. `(memberId, settlementMonth)` 유일 제약 위반(동시 요청 경쟁) 시 이미 저장된 정산을 다시 읽어 반환 (BR-STL-005)

### 4.2 거래 등록 흐름

결제: 금액 검증(BR-AMT) → 회원 확인(BR-TXN-006) → 시각 검증(BR-TIM-003) → `transactionId` 중복 판정(BR-TXN-003/004) → 저장

환불: 금액 검증 → 회원 확인 → 시각 검증 → `transactionId` 중복 판정 → 원거래 조회·검증(BR-RFD-001, 005, 006) → **원거래 잠금** 후 잔여액 검증(BR-RFD-004) → 저장

> 중복 판정은 원거래 검증보다 먼저 한다. 이미 처리된 환불을 같은 내용으로 재요청하면, 잔여액이 0이더라도 `REFUND_EXCEEDS_REMAINING`이 아니라 기존 환불을 반환해야 하기 때문이다 (TC-DUP-03).

## 5. 외부 의존성 (포트)

테스트에서 Fake 구현으로 교체할 수 있도록 다음을 경계로 둔다(작업 원칙 10).

| 포트 | 역할 | 테스트 대역 |
|------|------|-------------|
| `Clock` | 현재 시각 제공 (BR-TIM-004) | 고정/조작 가능한 시계 |
| `MemberRepository` | 회원 존재 확인 | 메모리 Fake |
| `TransactionRepository` | 결제·환불 저장, 귀속월·asOf 조건 조회, 결제 잠금 | 메모리 Fake (잠금 포함) |
| `SettlementRepository` | 정산 저장·조회, (회원, 월) 유일 보장 | 메모리 Fake |

## 6. 영속화 제약 (이후 Phase 참고용)

| 제약 | 근거 |
|------|------|
| 거래 테이블 `transaction_id` UNIQUE (결제·환불 공통) | BR-TXN-002 |
| 정산 테이블 `(member_id, settlement_month)` UNIQUE | BR-STL-005 |
| 금액 컬럼 `BIGINT`, CHECK `amount BETWEEN 1 AND 1000000000` | BR-AMT-002, BR-AMT-003 |
| 시각 컬럼 `TIMESTAMPTZ` | BR-TIM-001 |
| 환불의 원거래 FK + 결제 행 잠금(`SELECT … FOR UPDATE`) 또는 version 컬럼 | BR-RFD-004 |

## 7. 외부 인터페이스 (초안)

HTTP 경로와 상태 코드는 **초안**이다. 확정은 OPEN-11에서 한다.

| 기능 | 요청 | 성공 응답 |
|------|------|-----------|
| 결제 등록 | `POST /members/{memberId}/payments` `{transactionId, amount, occurredAt}` | 최초 201 / 동일 재요청 200 |
| 환불 등록 | `POST /members/{memberId}/refunds` `{transactionId, originalTransactionId, amount, refundedAt}` | 최초 201 / 동일 재요청 200 |
| 정산 요청 | `POST /members/{memberId}/settlements/{YYYY-MM}` | 최초 201 / 재요청 200 (본문 동일) |
| 정산 조회 | `GET /members/{memberId}/settlements/{YYYY-MM}` | 200 / 없으면 404 |

| 오류 코드 | HTTP (초안) |
|-----------|-------------|
| `INVALID_REQUEST`, `INVALID_AMOUNT`, `INVALID_SETTLEMENT_MONTH`, `INVALID_REFUND_TIME`, `INVALID_REFUND_TARGET`, `TRANSACTION_TIME_OUT_OF_OPEN_PERIOD` | 400 |
| `MEMBER_NOT_FOUND`, `PAYMENT_NOT_FOUND` | 404 |
| `DUPLICATE_TRANSACTION_CONFLICT`, `REFUND_EXCEEDS_REMAINING`, `SETTLEMENT_PERIOD_NOT_CLOSED` | 409 |
| `AMOUNT_OVERFLOW`, `NEGATIVE_NET_USAGE` | 500 (무결성 위반) |

회원 등록 방법(API 또는 사전 데이터)은 OPEN-07에서 결정한다.
