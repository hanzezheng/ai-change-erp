package com.nongpi.assistant.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record UpdateOrderRequest(
        @NotBlank String customerId,
        @NotNull LocalDate transactionDate,
        @NotEmpty @Valid List<OrderItemRequest> items,
        @NotNull Instant expectedModifiedAt,
        String note
) {
}
