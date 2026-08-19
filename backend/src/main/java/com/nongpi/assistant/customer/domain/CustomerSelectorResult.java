package com.nongpi.assistant.customer.domain;

import java.util.List;

/**
 * 客户选择器返回（docs/06_API_DATA_DESIGN.md #9）。
 *
 * <p>{@code recent} 需要按租户查询最近成交客户（Sales Order 历史），
 * 属于订单阶段。本轮固定为空数组，不用「随便取前几个客户」冒充最近交易客户。
 */
public record CustomerSelectorResult(
        List<CustomerSummary> recent,
        List<CustomerSummary> results
) {
}
