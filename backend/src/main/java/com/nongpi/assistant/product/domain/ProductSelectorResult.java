package com.nongpi.assistant.product.domain;

import java.util.List;

/**
 * 商品选择器返回（docs/06_API_DATA_DESIGN.md #14）。
 *
 * <p>{@code frequentItems}（该客户常买商品）需要按 Tenant + Customer 查询
 * Sales Order 历史，属于订单阶段。本轮固定为空数组，不用「随便挑几个商品」冒充常买。
 * 同理，各行也不返回 lastDealPrice。
 */
public record ProductSelectorResult(
        List<ProductVariant> frequentItems,
        List<ProductVariant> results
) {
}
