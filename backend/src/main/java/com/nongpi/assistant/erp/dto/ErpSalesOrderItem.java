package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpSalesOrderItem(
        @JsonProperty("name") String name,
        @JsonProperty("parent") String parent,
        @JsonProperty("item_code") String itemCode,
        @JsonProperty("item_name") String itemName,
        @JsonProperty("qty") BigDecimal qty,
        @JsonProperty("uom") String uom,
        @JsonProperty("rate") BigDecimal rate,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("conversion_factor") BigDecimal conversionFactor,
        @JsonProperty("idx") Integer idx
) {
    public static final String DOCTYPE = "Sales Order Item";
}
