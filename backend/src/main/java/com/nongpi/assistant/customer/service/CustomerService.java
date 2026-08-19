package com.nongpi.assistant.customer.service;

import com.nongpi.assistant.common.api.PageRequestParams;
import com.nongpi.assistant.common.api.PageResponse;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.customer.domain.CustomerSelectorResult;
import com.nongpi.assistant.customer.domain.CustomerSummary;
import com.nongpi.assistant.erp.adapter.CustomerErpAdapter;
import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.erp.connection.ErpConnectionProvider;
import com.nongpi.assistant.identity.CustomerAliasProvider;
import com.nongpi.assistant.tenant.TenantContext;
import com.nongpi.assistant.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CustomerService {

    private static final int SELECTOR_RESULT_LIMIT = 20;

    private final CustomerErpAdapter customerErpAdapter;
    private final ErpConnectionProvider erpConnectionProvider;
    private final CustomerAliasProvider customerAliasProvider;

    public CustomerService(CustomerErpAdapter customerErpAdapter,
                           ErpConnectionProvider erpConnectionProvider,
                           CustomerAliasProvider customerAliasProvider) {
        this.customerErpAdapter = customerErpAdapter;
        this.erpConnectionProvider = erpConnectionProvider;
        this.customerAliasProvider = customerAliasProvider;
    }

    public PageResponse<CustomerSummary> search(String keyword, PageRequestParams page) {
        TenantContext tenant = TenantContextHolder.require();
        ErpConnection connection = erpConnectionProvider.resolve(tenant);

        // 多取一条用于判断是否还有下一页，ERPNext 列表接口不返回总数。
        List<CustomerSummary> fetched =
                customerErpAdapter.search(connection, keyword, page.offset(), page.pageSize() + 1);
        boolean hasMore = fetched.size() > page.pageSize();
        List<CustomerSummary> content = hasMore ? fetched.subList(0, page.pageSize()) : fetched;

        return PageResponse.of(withAliases(tenant, content), page.page(), page.pageSize(), hasMore);
    }

    public CustomerSummary getById(String customerId) {
        TenantContext tenant = TenantContextHolder.require();
        ErpConnection connection = erpConnectionProvider.resolve(tenant);
        CustomerSummary customer = customerErpAdapter.findById(connection, customerId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.CUSTOMER_NOT_FOUND,
                        BusinessErrorCode.CUSTOMER_NOT_FOUND.defaultMessage(),
                        Map.of("customerId", customerId)));
        return withAliases(tenant, List.of(customer)).get(0);
    }

    /**
     * 客户选择器。{@code recent}（最近成交客户）需要 Sales Order 历史，属于订单阶段，
     * 本轮返回空数组而不是拿任意客户充数。
     */
    public CustomerSelectorResult selector(String keyword) {
        TenantContext tenant = TenantContextHolder.require();
        ErpConnection connection = erpConnectionProvider.resolve(tenant);
        List<CustomerSummary> results =
                customerErpAdapter.search(connection, keyword, 0, SELECTOR_RESULT_LIMIT);
        return new CustomerSelectorResult(List.of(), withAliases(tenant, results));
    }

    private List<CustomerSummary> withAliases(TenantContext tenant, List<CustomerSummary> customers) {
        if (customers.isEmpty()) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        customers.forEach(customer -> ids.add(customer.customerId()));
        Map<String, List<String>> aliases = customerAliasProvider.findAliases(tenant, ids);

        List<CustomerSummary> enriched = new ArrayList<>(customers.size());
        for (CustomerSummary customer : customers) {
            enriched.add(new CustomerSummary(
                    customer.customerId(),
                    customer.customerName(),
                    aliases.getOrDefault(customer.customerId(), List.of()),
                    customer.phone(),
                    customer.address()
            ));
        }
        return enriched;
    }
}
