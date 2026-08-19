package com.nongpi.assistant.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 正式收款。{@code paymentId} 等于 ERPNext {@code Payment Entry.name}。
 */
public record Payment(
        String paymentId,
        String customerId,
        String customerName,
        String relatedOrderId,
        BigDecimal amount,
        String paymentMethodId,
        String paymentMethodName,
        PaymentConfirmationStatus paymentStatus,
        String paymentStatusLabel,
        String referenceNo,
        LocalDate referenceDate,
        Instant createdAt,
        Instant updatedAt
) {
}
