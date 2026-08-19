package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * ERPNext {@code UOM Conversion Detail} 子表，挂在 Item 的 {@code uoms} 字段下。
 * 这是每个商品「允许使用哪些单位」的正式来源。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpUomConversion(
        @JsonProperty("parent") String parent,
        @JsonProperty("uom") String uom,
        @JsonProperty("conversion_factor") BigDecimal conversionFactor,
        @JsonProperty("idx") Integer idx
) {
    public static final String DOCTYPE = "UOM Conversion Detail";
}
