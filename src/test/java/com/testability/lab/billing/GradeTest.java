package com.testability.lab.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GradeTest {

    @DisplayName("BR-GRD-001: 등급 실적의 구간 경계에서 하한 이상이면 해당 등급이다")
    @ParameterizedTest(name = "GB {0} → {1}")
    @CsvSource({
            "0, BASIC",
            "999999, BASIC",
            "1000000, SILVER",
            "4999999, SILVER",
            "5000000, GOLD",
            "9999999, GOLD",
            "10000000, VIP",
            "2147483647, VIP",
            "2147483648, VIP",
            "3000000000, VIP"
    })
    void gradeByBoundary(long gradeBasisAmount, Grade expected) {
        assertThat(Grade.of(gradeBasisAmount)).isEqualTo(expected);
    }

    @DisplayName("BR-GRD-001, BR-FEE-002: 등급별 요율은 BASIC 300bp, SILVER 200bp, GOLD 100bp, VIP 50bp")
    @ParameterizedTest(name = "{0} → {1}bp")
    @CsvSource({
            "BASIC, 300",
            "SILVER, 200",
            "GOLD, 100",
            "VIP, 50"
    })
    void rateBasisPoints(Grade grade, int expectedBasisPoints) {
        assertThat(grade.rateBasisPoints()).isEqualTo(expectedBasisPoints);
    }

    @DisplayName("BR-GRD-001: 음수 등급 실적은 등급 구간에 없으므로 오류다 (TEST_CASE_MATRIX 1.1)")
    @Test
    void negativeGradeBasisAmountIsRejected() {
        assertThatThrownBy(() -> Grade.of(-1L))
                .isInstanceOf(RuntimeException.class);
    }
}
