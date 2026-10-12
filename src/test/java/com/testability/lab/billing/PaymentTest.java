package com.testability.lab.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T03:00:00Z");

    @DisplayName("BR-AMT-002: 결제 금액 1원과 1,000,000,000원은 허용한다 (TC-AMT-01, TC-AMT-04)")
    @ParameterizedTest(name = "{0}원")
    @ValueSource(longs = {1L, 1_000_000_000L})
    void acceptsAmountWithinRange(long amount) {
        Payment payment = new Payment(amount, OCCURRED_AT);

        assertThat(payment.amount()).isEqualTo(amount);
        assertThat(payment.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @DisplayName("BR-AMT-002: 범위 밖 금액은 INVALID_AMOUNT로 거부한다 (TC-AMT-02, 03, 05, 06)")
    @ParameterizedTest(name = "{0}원")
    @ValueSource(longs = {0L, -1L, 1_000_000_001L, 2_147_483_648L})
    void rejectsAmountOutOfRange(long amount) {
        assertThatThrownBy(() -> new Payment(amount, OCCURRED_AT))
                .hasMessageContaining("INVALID_AMOUNT");
    }
}
