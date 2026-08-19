package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * ERPNext {@code Bin} DocType，按「商品 × 仓库」保存库存余额，是库存事实来源。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpBin(
        @JsonProperty("item_code") String itemCode,
        @JsonProperty("warehouse") String warehouse,
        @JsonProperty("actual_qty") BigDecimal actualQty,
        @JsonProperty("stock_uom") String stockUom
) {
    public static final String DOCTYPE = "Bin";
}
