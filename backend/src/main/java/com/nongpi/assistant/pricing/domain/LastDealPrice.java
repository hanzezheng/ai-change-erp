package com.nongpi.assistant.pricing.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record LastDealPrice(
        BigDecimal price,
        String uom,
        String sourceOrderId,
        Instant transactionTime
) {
}
