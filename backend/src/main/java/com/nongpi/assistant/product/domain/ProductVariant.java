package com.nongpi.assistant.product.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 面向 App 的商品（docs/06_API_DATA_DESIGN.md #11）。
 *
 * <p>身份语义已冻结为两个字段：
 * <ul>
 *   <li>{@code itemCode} ← ERPNext {@code Item.item_code}。这是可交易 ERPNext Item
 *       的唯一正式身份，订单行只认它。ERPNext 中 {@code Item.name == Item.item_code}，
 *       不存在独立的变体主键，系统也不制造合成主键。</li>
 *   <li>{@code productId} ← 变体商品取 {@code Item.variant_of}，非变体商品取自身
 *       {@code item_code}。它只用于商品模板 / 商品族分组，不是可交易身份。</li>
 * </ul>
 *
 * <p>例如：苹果80果的 {@code productId} 是 {@code APPLE}、{@code itemCode} 是
 * {@code APPLE-80}；香蕉粉蕉不是变体，两者同为 {@code BANANA-FEN}。
 *
 * <p>{@code referencePrice} 是 {@code defaultUom} 对应的参考价，
 * 边界见 {@link AllowedUom#referencePrice()}：只用于展示与默认值，不是成交价。
 *
 * <p>{@code aliases} 来自 SaaS Product Identity，Identity 模块未实现时为空数组。
 */
public record ProductVariant(
        String productId,
        String itemCode,
        String productName,
        String spec,
        List<String> aliases,
        String defaultUom,
        List<AllowedUom> allowedUoms,
        BigDecimal referencePrice,
        String priceUom,
        String currency
) {
}
