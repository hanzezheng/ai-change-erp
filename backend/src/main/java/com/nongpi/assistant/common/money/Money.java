package com.nongpi.assistant.common.money;

import com.nongpi.assistant.order.domain.PaymentCollectionStatus;

import java.math.BigDecimal;

public final class Money {

    private Money() {
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal remainingToCollect(BigDecimal total, BigDecimal confirmedPaid) {
        BigDecimal remaining = zeroIfNull(total).subtract(zeroIfNull(confirmedPaid));
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }

    public static PaymentCollectionStatus collectionStatus(BigDecimal total, BigDecimal confirmedPaid) {
        BigDecimal paid = zeroIfNull(confirmedPaid);
        BigDecimal orderTotal = zeroIfNull(total);
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return PaymentCollectionStatus.UNPAID;
        }
        if (paid.compareTo(orderTotal) < 0) {
            return PaymentCollectionStatus.PARTIAL;
        }
        return PaymentCollectionStatus.PAID;
    }
}
