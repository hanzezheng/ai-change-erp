package com.nongpi.assistant.product.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 面向 App 的商品变体（docs/06_API_DATA_DESIGN.md #11）。
 *
 * <p>身份字段与 ERPNext 的对应关系：
 * <ul>
 *   <li>{@code itemCode} ← ERPNext {@code Item.item_code}</li>
 *   <li>{@code variantId} ← ERPNext {@code Item.name}。ERPNext 里
 *       {@code Item.name == Item.item_code}，不存在独立的变体主键，
 *       因此这两个字段取值相同。文档 #6 的示例把它们写成两个不同值，
 *       但 ERPNext 是事实源，这里不伪造合成 ID。</li>
 *   <li>{@code productId} ← ERPNext {@code Item.variant_of}（变体所属模板）。
 *       非变体商品没有模板，回落为自身 item_code。</li>
 * </ul>
 *
 * <p>{@code referencePrice} 是 {@code defaultUom} 对应的参考价。
 * 各 UOM 的价格在 {@code allowedUoms} 中逐个给出。
 *
 * <p>{@code aliases} 来自 SaaS Product Identity，Identity 模块未实现时为空数组。
 */
public record ProductVariant(
        String productId,
        String variantId,
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
