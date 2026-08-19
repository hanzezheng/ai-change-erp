package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpModeOfPaymentAccount(
        @JsonProperty("parent") String parent,
        @JsonProperty("company") String company,
        @JsonProperty("default_account") String defaultAccount
) {
    public static final String DOCTYPE = "Mode of Payment Account";
}
