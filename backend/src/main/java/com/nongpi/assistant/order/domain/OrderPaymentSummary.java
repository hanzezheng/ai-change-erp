package com.nongpi.assistant.order.domain;

import java.math.BigDecimal;

public record OrderPaymentSummary(
        BigDecimal orderTotal,
        BigDecimal confirmedPaid,
        BigDecimal remainingToCollect,
        PaymentCollectionStatus paymentStatus
) {
}
