package com.nongpi.assistant.inventory.domain;

import java.math.BigDecimal;

/**
 * 面向 App 的库存行（docs/06_API_DATA_DESIGN.md #40）。
 *
 * <p>库存事实来自 ERPNext Bin，按「商品 × 仓库」粒度返回。
 *
 * <p>身份语义与 {@link com.nongpi.assistant.product.domain.ProductVariant} 一致：
 * {@code itemCode} 是可交易 ERPNext Item 的唯一正式身份，
 * {@code productId} 只用于商品模板 / 商品族分组。
 *
 * <p>{@code alertQty} 只在 ERPNext 配置了补货预警线时才有值；
 * {@code lowStock} 也只有在此时才为 true/false，否则为 null，
 * 表示「未配置预警，无法判断」。禁止用 stock/500 之类的假百分比
 * （AGENTS.md #83、docs/06_API_DATA_DESIGN.md #41）。
 */
public record InventoryItem(
        String productId,
        String itemCode,
        String productName,
        String spec,
        BigDecimal quantity,
        String stockUom,
        String warehouse,
        BigDecimal alertQty,
        Boolean lowStock
) {
}
