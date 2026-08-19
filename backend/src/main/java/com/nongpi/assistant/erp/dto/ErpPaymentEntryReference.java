package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpPaymentEntryReference(
        @JsonProperty("reference_doctype") String referenceDoctype,
        @JsonProperty("reference_name") String referenceName,
        @JsonProperty("allocated_amount") BigDecimal allocatedAmount,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @JsonProperty("outstanding_amount") BigDecimal outstandingAmount
) {
    public static final String DOCTYPE = "Payment Entry Reference";
}
