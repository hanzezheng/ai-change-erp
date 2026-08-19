package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpModeOfPayment(
        @JsonProperty("name") String name,
        @JsonProperty("mode_of_payment") String modeOfPayment,
        @JsonProperty("type") String type,
        @JsonProperty("enabled") Integer enabled
) {
    public static final String DOCTYPE = "Mode of Payment";
}
