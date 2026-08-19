package com.nongpi.assistant.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePaymentRequest(
        @NotBlank String customerId,
        @NotBlank String relatedOrderId,
        @NotNull BigDecimal amount,
        @NotBlank String paymentMethodId,
        String referenceNo,
        LocalDate referenceDate,
        String note
) {
}
