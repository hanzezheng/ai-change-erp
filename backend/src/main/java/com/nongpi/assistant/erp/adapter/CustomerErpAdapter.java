package com.nongpi.assistant.erp.adapter;

import com.nongpi.assistant.customer.domain.CustomerSummary;
import com.nongpi.assistant.erp.connection.ErpConnection;

import java.util.List;
import java.util.Optional;

/**
 * 客户主数据的 ERP 出口。业务层只依赖本接口，不接触 ERPNext 字段与协议。
 */
public interface CustomerErpAdapter {

    /**
     * @param keyword 为空表示不过滤，按最近更新返回
     */
    List<CustomerSummary> search(ErpConnection connection, String keyword, int offset, int limit);

    Optional<CustomerSummary> findById(ErpConnection connection, String customerId);
}
