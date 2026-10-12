package com.testability.lab.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * TEST_CASE_MATRIX.md 중 EXPERIMENT_SCOPE.md §7에서 적용 가능하다고 정한 케이스.
 * 공통 전제: 정산월 2026-07, 요청 시각 2026-08-10T10:00:00+09:00.
 */
class FeeCalculatorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final String DEFAULT_NOW = "2026-08-10T10:00:00+09:00";
    private static final String IN_WINDOW = "2026-01-15T12:00:00+09:00";
    private static final String IN_JULY = "2026-07-15T12:00:00+09:00";

    private static Instant at(String isoWithOffset) {
        return OffsetDateTime.parse(isoWithOffset).toInstant();
    }

    private static Payment pay(long amount, String isoWithOffset) {
        return new Payment(amount, at(isoWithOffset));
    }

    private static FeeCalculator calculatorAt(String isoWithOffset) {
        return new FeeCalculator(Clock.fixed(at(isoWithOffset), SEOUL));
    }

    private static FeeResult settleJuly(List<Payment> payments) {
        return calculatorAt(DEFAULT_NOW).calculate(payments, JULY);
    }

    @Nested
    @DisplayName("등급 (TC-GRD)")
    class GradeCases {

        @DisplayName("TC-GRD-01: 산정 기간 거래가 없으면 BASIC, 당월 1,000,000원의 수수료는 30,000원")
        @Test
        void noHistoryIsBasic() {
            FeeResult result = settleJuly(List.of(pay(1_000_000L, IN_JULY)));

            assertThat(result.grade()).isEqualTo(Grade.BASIC);
            assertThat(result.gradeBasisAmount()).isZero();
            assertThat(result.feeBaseAmount()).isEqualTo(1_000_000L);
            assertThat(result.fee()).isEqualTo(30_000L);
        }

        @DisplayName("TC-GRD-02 ~ 07: 등급 경계값")
        @ParameterizedTest(name = "GB {0} → {1}, 수수료 {2}")
        @CsvSource({
                "999999, BASIC, 30000",
                "1000000, SILVER, 20000",
                "4999999, SILVER, 20000",
                "5000000, GOLD, 10000",
                "9999999, GOLD, 10000",
                "10000000, VIP, 5000"
        })
        void gradeBoundaries(long windowAmount, Grade expectedGrade, long expectedFee) {
            FeeResult result = settleJuly(List.of(
                    pay(windowAmount, IN_WINDOW),
                    pay(1_000_000L, IN_JULY)));

            assertThat(result.grade()).isEqualTo(expectedGrade);
            assertThat(result.gradeBasisAmount()).isEqualTo(windowAmount);
            assertThat(result.feeBaseAmount()).isEqualTo(1_000_000L);
            assertThat(result.rateBasisPoints()).isEqualTo(expectedGrade.rateBasisPoints());
            assertThat(result.fee()).isEqualTo(expectedFee);
        }

        @DisplayName("TC-GRD-08: 산정 기간 합계 3,000,000,000원도 정확히 합산되어 VIP")
        @Test
        void sumBeyondIntRange() {
            FeeResult result = settleJuly(List.of(
                    pay(1_000_000_000L, "2025-09-15T12:00:00+09:00"),
                    pay(1_000_000_000L, "2025-12-15T12:00:00+09:00"),
                    pay(1_000_000_000L, "2026-03-15T12:00:00+09:00"),
                    pay(1_000_000L, IN_JULY)));

            assertThat(result.grade()).isEqualTo(Grade.VIP);
            assertThat(result.gradeBasisAmount()).isEqualTo(3_000_000_000L);
            assertThat(result.fee()).isEqualTo(5_000L);
        }

        @DisplayName("TC-GRD-09: 여러 달에 나뉜 결제를 합산한다")
        @Test
        void sumAcrossMonths() {
            FeeResult result = settleJuly(List.of(
                    pay(300_000L, "2025-08-15T12:00:00+09:00"),
                    pay(300_000L, "2025-11-15T12:00:00+09:00"),
                    pay(400_000L, "2026-04-15T12:00:00+09:00"),
                    pay(1_000_000L, IN_JULY)));

            assertThat(result.grade()).isEqualTo(Grade.SILVER);
            assertThat(result.gradeBasisAmount()).isEqualTo(1_000_000L);
            assertThat(result.fee()).isEqualTo(20_000L);
        }
    }

    @Nested
    @DisplayName("기간 경계 (TC-PRD)")
    class PeriodCases {

        @DisplayName("TC-PRD-01: 산정 기간 첫 순간(2025-07-01 00:00)의 결제는 포함")
        @Test
        void windowStartIsIncluded() {
            FeeResult result = settleJuly(List.of(pay(1_000_000L, "2025-07-01T00:00:00+09:00")));

            assertThat(result.grade()).isEqualTo(Grade.SILVER);
            assertThat(result.gradeBasisAmount()).isEqualTo(1_000_000L);
            assertThat(result.feeBaseAmount()).isZero();
            assertThat(result.fee()).isZero();
        }

        @DisplayName("TC-PRD-02: 산정 기간 직전(2025-06-30 23:59:59.999999999)의 결제는 제외")
        @Test
        void justBeforeWindowIsExcluded() {
            FeeResult result = settleJuly(List.of(pay(1_000_000L, "2025-06-30T23:59:59.999999999+09:00")));

            assertThat(result.grade()).isEqualTo(Grade.BASIC);
            assertThat(result.gradeBasisAmount()).isZero();
            assertThat(result.feeBaseAmount()).isZero();
            assertThat(result.fee()).isZero();
        }

        @DisplayName("TC-PRD-03: 산정 기간 마지막 순간(2026-06-30 23:59:59.999999999)의 결제는 포함")
        @Test
        void windowEndIsIncluded() {
            FeeResult result = settleJuly(List.of(pay(1_000_000L, "2026-06-30T23:59:59.999999999+09:00")));

            assertThat(result.grade()).isEqualTo(Grade.SILVER);
            assertThat(result.gradeBasisAmount()).isEqualTo(1_000_000L);
            assertThat(result.feeBaseAmount()).isZero();
        }

        @DisplayName("TC-PRD-04: 당월 첫 순간(2026-07-01 00:00)의 결제는 등급에서 제외되고 수수료 기준액에 포함")
        @Test
        void settlementMonthStartGoesToFeeBaseOnly() {
            FeeResult result = settleJuly(List.of(pay(1_000_000L, "2026-07-01T00:00:00+09:00")));

            assertThat(result.grade()).isEqualTo(Grade.BASIC);
            assertThat(result.gradeBasisAmount()).isZero();
            assertThat(result.feeBaseAmount()).isEqualTo(1_000_000L);
            assertThat(result.fee()).isEqualTo(30_000L);
        }

        @DisplayName("TC-PRD-05: 13개월 전과 당월 결제를 섞어도 직전 12개월만 등급에 반영")
        @Test
        void onlyTwelveCompletedMonthsCount() {
            List<Payment> payments = new ArrayList<>();
            payments.add(pay(500_000L, "2025-06-15T12:00:00+09:00"));
            for (int i = 0; i < 12; i++) {
                YearMonth month = YearMonth.of(2025, 7).plusMonths(i);
                payments.add(pay(50_000L, month.atDay(15) + "T12:00:00+09:00"));
            }
            payments.add(pay(500_000L, IN_JULY));

            FeeResult result = settleJuly(payments);

            assertThat(result.grade()).isEqualTo(Grade.BASIC);
            assertThat(result.gradeBasisAmount()).isEqualTo(600_000L);
            assertThat(result.feeBaseAmount()).isEqualTo(500_000L);
            assertThat(result.fee()).isEqualTo(15_000L);
        }

        @DisplayName("TC-PRD-06: UTC로 표현된 시각도 Asia/Seoul 기준으로 귀속월을 판정")
        @Test
        void utcInstantIsJudgedInSeoulTime() {
            FeeResult july = settleJuly(List.of(new Payment(1_000_000L, Instant.parse("2026-06-30T15:00:00Z"))));
            FeeResult june = settleJuly(List.of(new Payment(1_000_000L, Instant.parse("2026-06-30T14:59:59Z"))));

            assertThat(july.grade()).isEqualTo(Grade.BASIC);
            assertThat(july.gradeBasisAmount()).isZero();
            assertThat(july.feeBaseAmount()).isEqualTo(1_000_000L);
            assertThat(july.fee()).isEqualTo(30_000L);

            assertThat(june.grade()).isEqualTo(Grade.SILVER);
            assertThat(june.gradeBasisAmount()).isEqualTo(1_000_000L);
            assertThat(june.feeBaseAmount()).isZero();
            assertThat(june.fee()).isZero();
        }

        @DisplayName("TC-PRD-07: 연도 경계 — 정산월 2026-01의 산정 기간은 2025-01 ~ 2025-12")
        @Test
        void yearBoundary() {
            FeeCalculator calculator = calculatorAt("2026-02-10T10:00:00+09:00");
            YearMonth january = YearMonth.of(2026, 1);

            FeeResult excluded = calculator.calculate(
                    List.of(pay(1_000_000L, "2024-12-31T23:59:59.999999999+09:00")), january);
            FeeResult included = calculator.calculate(
                    List.of(pay(1_000_000L, "2025-01-01T00:00:00+09:00")), january);

            assertThat(excluded.periodFrom()).isEqualTo(YearMonth.of(2025, 1));
            assertThat(excluded.periodTo()).isEqualTo(YearMonth.of(2025, 12));
            assertThat(excluded.grade()).isEqualTo(Grade.BASIC);
            assertThat(excluded.gradeBasisAmount()).isZero();
            assertThat(included.grade()).isEqualTo(Grade.SILVER);
            assertThat(included.gradeBasisAmount()).isEqualTo(1_000_000L);
        }

        @DisplayName("TC-PRD-08: 12개월 합산 결과가 등급 경계를 사이에 두는 경우")
        @ParameterizedTest(name = "매월 {0}원 × 12 = {1} → {2}")
        @CsvSource({
                "83334, 1000008, SILVER",
                "83333, 999996, BASIC"
        })
        void twelveMonthlyPayments(long monthlyAmount, long expectedSum, Grade expectedGrade) {
            List<Payment> payments = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                YearMonth month = YearMonth.of(2025, 7).plusMonths(i);
                payments.add(pay(monthlyAmount, month.atDay(15) + "T12:00:00+09:00"));
            }

            FeeResult result = settleJuly(payments);

            assertThat(result.gradeBasisAmount()).isEqualTo(expectedSum);
            assertThat(result.grade()).isEqualTo(expectedGrade);
        }

        @DisplayName("TC-PRD-09: 윤년 2월 29일 마지막 순간은 2월, 3월 1일 0시는 3월")
        @Test
        void leapYear() {
            FeeResult result = calculatorAt("2028-04-10T10:00:00+09:00").calculate(List.of(
                    pay(1_000_000L, "2028-02-29T23:59:59.999999999+09:00"),
                    pay(100L, "2028-03-01T00:00:00+09:00")), YearMonth.of(2028, 3));

            assertThat(result.grade()).isEqualTo(Grade.SILVER);
            assertThat(result.gradeBasisAmount()).isEqualTo(1_000_000L);
            assertThat(result.feeBaseAmount()).isEqualTo(100L);
            assertThat(result.fee()).isEqualTo(2L);
        }

        @DisplayName("TC-PRD-10 / TC-STL-01 일부: 결과에 산정 기간과 모든 계산 항목이 기록된다")
        @Test
        void resultRecordsAllFields() {
            FeeResult result = settleJuly(List.of(
                    pay(1_000_000L, IN_WINDOW),
                    pay(1_000_000L, IN_JULY)));

            assertThat(result).isEqualTo(new FeeResult(
                    JULY,
                    YearMonth.of(2025, 7),
                    YearMonth.of(2026, 6),
                    1_000_000L,
                    Grade.SILVER,
                    1_000_000L,
                    200,
                    20_000L));
        }
    }

    @Nested
    @DisplayName("수수료·반올림 (TC-FEE)")
    class FeeCases {

        @DisplayName("TC-FEE-01 ~ 11, 15: 원 단위 HALF_UP 반올림")
        @ParameterizedTest(name = "등급 준비 {0}원, 당월 {1}원 → {2}, 수수료 {3}원")
        @CsvSource({
                "0, 16, BASIC, 0",
                "0, 17, BASIC, 1",
                "0, 50, BASIC, 2",
                "1000000, 24, SILVER, 0",
                "1000000, 25, SILVER, 1",
                "5000000, 49, GOLD, 0",
                "5000000, 50, GOLD, 1",
                "5000000, 250, GOLD, 3",
                "10000000, 899, VIP, 4",
                "10000000, 900, VIP, 5",
                "10000000, 999, VIP, 5",
                "0, 10000000, BASIC, 300000"
        })
        void roundsHalfUpToWon(long windowAmount, long julyAmount, Grade expectedGrade, long expectedFee) {
            List<Payment> payments = new ArrayList<>();
            if (windowAmount > 0) {
                payments.add(pay(windowAmount, IN_WINDOW));
            }
            payments.add(pay(julyAmount, IN_JULY));

            FeeResult result = settleJuly(payments);

            assertThat(result.grade()).isEqualTo(expectedGrade);
            assertThat(result.feeBaseAmount()).isEqualTo(julyAmount);
            assertThat(result.fee()).isEqualTo(expectedFee);
        }

        @DisplayName("TC-FEE-12: 당월 거래가 없으면 VIP라도 수수료 0원")
        @Test
        void zeroFeeBase() {
            FeeResult result = settleJuly(List.of(pay(10_000_000L, IN_WINDOW)));

            assertThat(result.grade()).isEqualTo(Grade.VIP);
            assertThat(result.gradeBasisAmount()).isEqualTo(10_000_000L);
            assertThat(result.feeBaseAmount()).isZero();
            assertThat(result.fee()).isZero();
        }

        @DisplayName("TC-FEE-13: VIP 100원 × 3건은 월 합계 300원에 한 번 반올림해 2원 (거래별이면 3원)")
        @Test
        void roundsOnceOnMonthlyTotalVip() {
            FeeResult result = settleJuly(List.of(
                    pay(10_000_000L, IN_WINDOW),
                    pay(100L, "2026-07-05T12:00:00+09:00"),
                    pay(100L, "2026-07-15T12:00:00+09:00"),
                    pay(100L, "2026-07-25T12:00:00+09:00")));

            assertThat(result.feeBaseAmount()).isEqualTo(300L);
            assertThat(result.fee()).isEqualTo(2L);
        }

        @DisplayName("TC-FEE-14: BASIC 17원 × 2건은 월 합계 34원에 한 번 반올림해 1원 (거래별이면 2원)")
        @Test
        void roundsOnceOnMonthlyTotalBasic() {
            FeeResult result = settleJuly(List.of(
                    pay(17L, "2026-07-05T12:00:00+09:00"),
                    pay(17L, "2026-07-25T12:00:00+09:00")));

            assertThat(result.feeBaseAmount()).isEqualTo(34L);
            assertThat(result.fee()).isEqualTo(1L);
        }

        @DisplayName("TC-FEE-16, 17: 수수료 기준액 3,000,000,000원")
        @ParameterizedTest(name = "등급 준비 {0}원 → {1}, 수수료 {2}원")
        @CsvSource({
                "0, BASIC, 90000000",
                "10000000, VIP, 15000000"
        })
        void largeFeeBase(long windowAmount, Grade expectedGrade, long expectedFee) {
            List<Payment> payments = new ArrayList<>();
            if (windowAmount > 0) {
                payments.add(pay(windowAmount, IN_WINDOW));
            }
            payments.add(pay(1_000_000_000L, "2026-07-05T12:00:00+09:00"));
            payments.add(pay(1_000_000_000L, "2026-07-15T12:00:00+09:00"));
            payments.add(pay(1_000_000_000L, "2026-07-25T12:00:00+09:00"));

            FeeResult result = settleJuly(payments);

            assertThat(result.grade()).isEqualTo(expectedGrade);
            assertThat(result.feeBaseAmount()).isEqualTo(3_000_000_000L);
            assertThat(result.fee()).isEqualTo(expectedFee);
        }

        @DisplayName("BR-FEE-005, BR-PRF-005: 음수 수수료 기준액은 0으로 보정하지 않고 오류")
        @Test
        void negativeFeeBaseIsRejected() {
            FeeCalculator calculator = calculatorAt(DEFAULT_NOW);

            assertThatThrownBy(() -> calculator.calculateFee(-1L, Grade.BASIC))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("정수 범위 (TC-AMT-08)")
    class AmountRangeCases {

        @DisplayName("TC-AMT-08: 등급 실적이 int 최댓값과 최댓값+1이어도 정확히 합산")
        @Test
        void gradeBasisAroundIntMax() {
            List<Payment> payments = new ArrayList<>(List.of(
                    pay(1_000_000_000L, "2025-09-15T12:00:00+09:00"),
                    pay(1_000_000_000L, "2025-12-15T12:00:00+09:00"),
                    pay(147_483_647L, "2026-03-15T12:00:00+09:00")));

            FeeResult atMax = settleJuly(payments);
            payments.add(pay(1L, "2026-04-15T12:00:00+09:00"));
            FeeResult overMax = settleJuly(payments);

            assertThat(atMax.grade()).isEqualTo(Grade.VIP);
            assertThat(atMax.gradeBasisAmount()).isEqualTo(2_147_483_647L);
            assertThat(overMax.grade()).isEqualTo(Grade.VIP);
            assertThat(overMax.gradeBasisAmount()).isEqualTo(2_147_483_648L);
        }
    }

    @Nested
    @DisplayName("정산월 마감 (TC-STL-04, 05)")
    class SettlementPeriodCases {

        @DisplayName("TC-STL-04 (a): 정산월 마지막 순간에는 SETTLEMENT_PERIOD_NOT_CLOSED")
        @Test
        void rejectsJustBeforeMonthEnds() {
            FeeCalculator calculator = calculatorAt("2026-07-31T23:59:59.999999999+09:00");

            assertThatThrownBy(() -> calculator.calculate(List.of(), JULY))
                    .hasMessageContaining("SETTLEMENT_PERIOD_NOT_CLOSED");
        }

        @DisplayName("TC-STL-04 (b): 다음 달 첫 순간부터 정산할 수 있다")
        @Test
        void allowsAtNextMonthStart() {
            FeeResult result = calculatorAt("2026-08-01T00:00:00+09:00").calculate(List.of(), JULY);

            assertThat(result.grade()).isEqualTo(Grade.BASIC);
            assertThat(result.gradeBasisAmount()).isZero();
            assertThat(result.feeBaseAmount()).isZero();
            assertThat(result.fee()).isZero();
        }

        @DisplayName("TC-STL-05: 진행 중인 월(2026-08)과 미래 월(2026-09)은 거부")
        @ParameterizedTest(name = "{0}")
        @CsvSource({"2026-08", "2026-09"})
        void rejectsOpenOrFutureMonth(String month) {
            FeeCalculator calculator = calculatorAt(DEFAULT_NOW);

            assertThatThrownBy(() -> calculator.calculate(List.of(), YearMonth.parse(month)))
                    .hasMessageContaining("SETTLEMENT_PERIOD_NOT_CLOSED");
        }
    }

    @Nested
    @DisplayName("결정성 (TC-TIM-07)")
    class DeterminismCases {

        @DisplayName("TC-TIM-07: 같은 데이터와 같은 Clock이면 서로 다른 인스턴스도 같은 결과")
        @Test
        void sameInputSameResult() {
            List<Payment> payments = List.of(
                    pay(1_000_000L, IN_WINDOW),
                    pay(12_345L, IN_JULY));

            FeeResult first = calculatorAt(DEFAULT_NOW).calculate(payments, JULY);
            FeeResult second = calculatorAt(DEFAULT_NOW).calculate(payments, JULY);

            assertThat(first).isEqualTo(second);
        }
    }
}
