package com.nongpi.assistant.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 正式订单。{@code orderId} 等于 ERPNext {@code Sales Order.name}，不另造公开 ID。
 */
public record Order(
        String orderId,
        String customerId,
        String customerName,
        LocalDate transactionDate,
        List<OrderItem> items,
        OrderStatus orderStatus,
        String orderStatusLabel,
        PaymentCollectionStatus paymentStatus,
        String paymentStatusLabel,
        BigDecimal totalAmount,
        BigDecimal confirmedPaid,
        BigDecimal remainingToCollect,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {
}
