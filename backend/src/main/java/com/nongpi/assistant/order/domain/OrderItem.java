package com.nongpi.assistant.order.domain;

import java.math.BigDecimal;

/**
 * 订单行。{@code orderItemId} 等于 ERPNext {@code Sales Order Item.name}。
 */
public record OrderItem(
        String orderItemId,
        String productId,
        String itemCode,
        String productName,
        String spec,
        BigDecimal qty,
        String uom,
        BigDecimal rate,
        BigDecimal amount
) {
}
