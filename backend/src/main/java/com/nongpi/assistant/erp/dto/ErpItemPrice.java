package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * ERPNext {@code Item Price} DocType，商品参考价格的正式来源。
 * 系统不另建价格表（AGENTS.md 第六节）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpItemPrice(
        @JsonProperty("name") String name,
        @JsonProperty("item_code") String itemCode,
        @JsonProperty("price_list") String priceList,
        @JsonProperty("price_list_rate") BigDecimal priceListRate,
        @JsonProperty("currency") String currency,
        @JsonProperty("uom") String uom
) {
    public static final String DOCTYPE = "Item Price";
}
