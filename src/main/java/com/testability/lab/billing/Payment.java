package com.testability.lab.billing;

import java.time.Instant;
import java.util.Objects;

/**
 * 결제 1건. 이번 실험에서는 환불과 중복 판정을 다루지 않으므로 금액과 발생 시각만 가진다.
 */
public record Payment(long amount, Instant occurredAt) {

    public static final long MIN_AMOUNT = 1L;
    public static final long MAX_AMOUNT = 1_000_000_000L;

    public Payment {
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("INVALID_AMOUNT: " + amount);
        }
    }
}
