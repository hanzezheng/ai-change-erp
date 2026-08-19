package com.nongpi.assistant.order.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummary(
        String orderId,
        String customerId,
        String customerName,
        String itemSummary,
        int itemCount,
        BigDecimal totalAmount,
        OrderStatus orderStatus,
        PaymentCollectionStatus paymentStatus,
        Instant transactionTime
) {
}
