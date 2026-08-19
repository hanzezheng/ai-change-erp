package com.nongpi.assistant.erp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * ERPNext {@code Item} DocType 的原始投影。
 *
 * <p>ERPNext 中 {@code Item.name == Item.item_code}，模板与变体都是 Item，
 * 变体通过 {@code variant_of} 指回模板。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErpItem(
        @JsonProperty("name") String name,
        @JsonProperty("item_code") String itemCode,
        @JsonProperty("item_name") String itemName,
        @JsonProperty("description") String description,
        @JsonProperty("stock_uom") String stockUom,
        @JsonProperty("sales_uom") String salesUom,
        @JsonProperty("variant_of") String variantOf,
        @JsonProperty("item_group") String itemGroup,
        @JsonProperty("has_variants") Integer hasVariants,
        @JsonProperty("disabled") Integer disabled,
        @JsonProperty("is_sales_item") Integer isSalesItem,
        @JsonProperty("safety_stock") BigDecimal safetyStock
) {
    public static final String DOCTYPE = "Item";

    /**
     * ERPNext 允许 {@code item_code} 与 {@code name} 都出现在响应里，
     * 但列表查询按 fields 投影时可能只回其一。
     */
    public String resolvedItemCode() {
        return itemCode != null ? itemCode : name;
    }
}
