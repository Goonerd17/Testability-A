package com.testability.lab.billing;

/**
 * 회원 등급과 등급별 수수료율 (BR-GRD-001).
 * 수수료율은 부동소수점 오차를 피하기 위해 베이시스 포인트(1bp = 0.01%)로 보관한다.
 */
public enum Grade {

    BASIC(0L, 300),
    SILVER(1_000_000L, 200),
    GOLD(5_000_000L, 100),
    VIP(10_000_000L, 50);

    private final long minimumAmount;
    private final int rateBasisPoints;

    Grade(long minimumAmount, int rateBasisPoints) {
        this.minimumAmount = minimumAmount;
        this.rateBasisPoints = rateBasisPoints;
    }

    public long minimumAmount() {
        return minimumAmount;
    }

    public int rateBasisPoints() {
        return rateBasisPoints;
    }

    /**
     * 등급 실적으로 등급을 결정한다. 하한 이상이면 해당 등급이다 (BR-GRD-001).
     */
    public static Grade of(long gradeBasisAmount) {
        if (gradeBasisAmount < 0) {
            throw new IllegalArgumentException("등급 실적은 음수일 수 없다: " + gradeBasisAmount);
        }
        if (gradeBasisAmount >= VIP.minimumAmount) {
            return VIP;
        }
        if (gradeBasisAmount >= GOLD.minimumAmount) {
            return GOLD;
        }
        if (gradeBasisAmount >= SILVER.minimumAmount) {
            return SILVER;
        }
        return BASIC;
    }
}
