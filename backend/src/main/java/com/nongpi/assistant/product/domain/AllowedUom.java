package com.nongpi.assistant.product.domain;

import java.math.BigDecimal;

/**
 * 某个商品允许使用的一个计量单位。
 *
 * <p>来源是 ERPNext Item 的 {@code uoms} 子表，禁止客户端维护全局单位列表
 * （AGENTS.md #30、docs/06_API_DATA_DESIGN.md #12）。
 *
 * <p>{@code conversionFactor} 来自 ERPNext 的换算配置；子表缺失该行时为 null，
 * 系统不自己推算换算率（docs/06_API_DATA_DESIGN.md #11）。
 *
 * <p>{@code referencePrice} 取自 ERPNext Item Price 中同 UOM 的价格行，
 * 与 UOM 绑定：箱价与斤价各自独立，切换单位必须换成该单位的价格（AGENTS.md #32）。
 * ERPNext 没有该 UOM 的 Item Price 时为 null。
 *
 * <p><b>referencePrice 用途边界</b>：只用于 Product Selector 展示和订单行的默认参考价，
 * 不是权威成交价格计算结果。ERPNext 的实际定价还要结合 Selling Price List、Customer、
 * UOM、Qty、Currency、Transaction Date、Pricing Rule 等正式业务上下文。订单模块必须
 * 通过 ERPNext 正式定价链路取得成交价，禁止把这里的值当作 ERPNext 最终定价结果。
 */
public record AllowedUom(
        String uom,
        BigDecimal conversionFactor,
        BigDecimal referencePrice,
        String currency
) {
}
