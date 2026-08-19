package com.nongpi.assistant.customer.domain;

import java.util.List;

/**
 * 面向 App 的客户摘要（docs/06_API_DATA_DESIGN.md #7）。
 *
 * <p>文档中的 {@code receivableAmount} / {@code recentOrderTime} 需要查询 ERPNext
 * 财务事实与销售订单历史，属于后续阶段。本轮不返回这两个字段，
 * 而不是先填 0 —— 假的应收金额比没有更危险。
 */
public record CustomerSummary(
        String customerId,
        String customerName,
        List<String> aliases,
        String phone,
        String address
) {
}
