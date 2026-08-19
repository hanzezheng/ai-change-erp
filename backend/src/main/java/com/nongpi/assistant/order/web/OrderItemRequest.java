package com.nongpi.assistant.order.web;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record OrderItemRequest(
        String orderItemId,
        @NotBlank String itemCode,
        BigDecimal qty,
        @NotBlank String uom,
        BigDecimal rate
) {
}
