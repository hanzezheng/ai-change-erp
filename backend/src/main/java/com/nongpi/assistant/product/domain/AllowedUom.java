package com.nongpi.assistant.product.domain;

import java.math.BigDecimal;

/**
 * 某个商品允许使用的一个计量单位。
 *
 * <p>来源是 ERPNext Item 的 {@code uoms} 子表，禁止客户端维护全局单位列表
 * （AGENTS.md #30、docs/06_API_DATA_DESIGN.md #12）。
 *
 * <p>{@code referencePrice} 与该 UOM 绑定：从「箱」切到「斤」必须换成斤的价格，
 * 不能沿用箱价（AGENTS.md #32）。ERPNext 没有该 UOM 的 Item Price 时为 null。
 *
 * <p>{@code conversionFactor} 来自 ERPNext 的换算配置；子表缺失该行时为 null，
 * 系统不自己推算换算率（docs/06_API_DATA_DESIGN.md #11）。
 */
public record AllowedUom(
        String uom,
        BigDecimal conversionFactor,
        BigDecimal referencePrice,
        String currency
) {
}
