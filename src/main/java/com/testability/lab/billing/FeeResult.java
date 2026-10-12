package com.testability.lab.billing;

import java.time.YearMonth;

/**
 * 정산월 하나에 대한 수수료 계산 결과 (BR-STL-011 중 이번 범위에 해당하는 항목).
 */
public record FeeResult(
        YearMonth settlementMonth,
        YearMonth periodFrom,
        YearMonth periodTo,
        long gradeBasisAmount,
        Grade grade,
        long feeBaseAmount,
        int rateBasisPoints,
        long fee
) {
}
