package com.nongpi.assistant.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record AiActionRequest(
        @NotNull InputType inputType,
        String text,
        String asrText,
        @Valid AiContextDto context
) {
    public enum InputType {
        TEXT,
        VOICE
    }

    public record AiContextDto(
            String currentPage,
            String currentOrderId,
            String currentCustomerId,
            String currentCustomerName,
            List<ContextItemDto> currentItems
    ) {
        public AiContextDto {
            if (currentItems == null) {
                currentItems = List.of();
            }
        }
    }

    public record ContextItemDto(
            String itemCode,
            String productId,
            String productName,
            String spec,
            BigDecimal qty,
            String uom,
            BigDecimal rate
    ) {
    }

    public String resolvedText() {
        if (text != null && !text.isBlank()) {
            return text.trim();
        }
        if (asrText != null && !asrText.isBlank()) {
            return asrText.trim();
        }
        return "";
    }
}
