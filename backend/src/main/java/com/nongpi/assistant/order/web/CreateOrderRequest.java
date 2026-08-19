package com.nongpi.assistant.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerId,
        LocalDate transactionDate,
        @NotEmpty @Valid List<OrderItemRequest> items,
        String note
) {
}
