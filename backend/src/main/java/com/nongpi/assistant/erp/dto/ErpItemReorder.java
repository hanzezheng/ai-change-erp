package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * ERPNext {@code Item Reorder} 子表，商品在某个仓库的补货预警线。
 * 只有存在该配置时才允许展示「低库存」（AGENTS.md #83、docs/05_UI_SPEC.md #38）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpItemReorder(
        @JsonProperty("parent") String parent,
        @JsonProperty("warehouse") String warehouse,
        @JsonProperty("warehouse_reorder_level") BigDecimal warehouseReorderLevel
) {
    public static final String DOCTYPE = "Item Reorder";
}
