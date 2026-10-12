package com.testability.lab.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * 한 회원의 결제 목록으로 정산월의 등급과 수수료를 계산한다.
 *
 * <p>전달받은 결제 목록은 모두 같은 회원의 것이라고 가정한다.
 */
public class FeeCalculator {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int PERFORMANCE_MONTHS = 12;
    private static final BigDecimal BASIS_POINT_DIVISOR = BigDecimal.valueOf(10_000);

    private final Clock clock;

    public FeeCalculator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 정산월의 등급, 수수료 기준액, 수수료를 계산한다.
     * 정산월이 아직 끝나지 않았으면 거부한다 (BR-STL-002).
     */
    public FeeResult calculate(List<Payment> payments, YearMonth settlementMonth) {
        Objects.requireNonNull(payments, "payments");
        Objects.requireNonNull(settlementMonth, "settlementMonth");

        Instant now = clock.instant();
        if (now.isBefore(startOf(settlementMonth.plusMonths(1)))) {
            throw new IllegalStateException("SETTLEMENT_PERIOD_NOT_CLOSED: " + settlementMonth);
        }

        long gradeBasisAmount = cumulativeUsage(payments, settlementMonth);
        Grade grade = Grade.of(gradeBasisAmount);
        long feeBaseAmount = monthlyUsage(payments, settlementMonth);
        long fee = calculateFee(feeBaseAmount, grade);

        return new FeeResult(
                settlementMonth,
                settlementMonth.minusMonths(PERFORMANCE_MONTHS),
                settlementMonth.minusMonths(1),
                gradeBasisAmount,
                grade,
                feeBaseAmount,
                grade.rateBasisPoints(),
                fee
        );
    }

    /**
     * 정산월 직전 12개 완료 월의 누적 사용금액 (BR-PRF-002, BR-PRF-003).
     * 정산월 자체의 거래는 포함하지 않는다.
     */
    public long cumulativeUsage(List<Payment> payments, YearMonth settlementMonth) {
        YearMonth from = settlementMonth.minusMonths(PERFORMANCE_MONTHS);
        YearMonth to = settlementMonth.minusMonths(1);

        long sum = 0L;
        for (Payment payment : payments) {
            YearMonth month = attributionMonth(payment);
            if (!month.isBefore(from) && !month.isAfter(to)) {
                sum = Math.addExact(sum, payment.amount());
            }
        }
        return sum;
    }

    /**
     * 정산월 자체의 사용금액. 수수료 기준액이 된다 (BR-FEE-001).
     */
    public long monthlyUsage(List<Payment> payments, YearMonth month) {
        long sum = 0L;
        for (Payment payment : payments) {
            if (attributionMonth(payment).equals(month)) {
                sum = Math.addExact(sum, payment.amount());
            }
        }
        return sum;
    }

    /**
     * 수수료 = 기준액 × 요율, 원 단위 HALF_UP 반올림을 월 합계에 한 번만 적용한다 (BR-FEE-002, BR-FEE-003).
     */
    public long calculateFee(long feeBaseAmount, Grade grade) {
        if (feeBaseAmount < 0) {
            throw new IllegalArgumentException("수수료 기준액은 음수일 수 없다: " + feeBaseAmount);
        }
        return BigDecimal.valueOf(feeBaseAmount)
                .multiply(BigDecimal.valueOf(grade.rateBasisPoints()))
                .divide(BASIS_POINT_DIVISOR, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    static YearMonth attributionMonth(Payment payment) {
        return YearMonth.from(payment.occurredAt().atZone(ZONE));
    }

    static Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ZONE).toInstant();
    }
}
