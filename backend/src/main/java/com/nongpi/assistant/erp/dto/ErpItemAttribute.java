package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ERPNext {@code Item Variant Attribute} 子表，变体商品的规格来源。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpItemAttribute(
        @JsonProperty("parent") String parent,
        @JsonProperty("attribute") String attribute,
        @JsonProperty("attribute_value") String attributeValue,
        @JsonProperty("idx") Integer idx
) {
    public static final String DOCTYPE = "Item Variant Attribute";
}
