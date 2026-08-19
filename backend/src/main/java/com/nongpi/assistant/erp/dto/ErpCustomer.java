package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ERPNext {@code Customer} DocType 的原始投影。ERPNext 字段名只在本包内出现。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpCustomer(
        @JsonProperty("name") String name,
        @JsonProperty("customer_name") String customerName,
        @JsonProperty("mobile_no") String mobileNo,
        @JsonProperty("primary_address") String primaryAddress,
        @JsonProperty("customer_group") String customerGroup,
        @JsonProperty("disabled") Integer disabled
) {
    public static final String DOCTYPE = "Customer";
}
