package com.nongpi.assistant.erp.adapter.frappe;

import com.nongpi.assistant.customer.domain.CustomerSummary;
import com.nongpi.assistant.erp.adapter.CustomerErpAdapter;
import com.nongpi.assistant.erp.client.ErpFilter;
import com.nongpi.assistant.erp.client.ErpQuery;
import com.nongpi.assistant.erp.client.ErpRestClient;
import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.erp.dto.ErpCustomer;
import com.nongpi.assistant.erp.mapper.CustomerErpMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class FrappeCustomerErpAdapter implements CustomerErpAdapter {

    private static final String[] FIELDS = {
            "name", "customer_name", "mobile_no", "primary_address", "customer_group", "disabled"
    };

    private final ErpRestClient erpRestClient;
    private final CustomerErpMapper mapper;

    public FrappeCustomerErpAdapter(ErpRestClient erpRestClient, CustomerErpMapper mapper) {
        this.erpRestClient = erpRestClient;
        this.mapper = mapper;
    }

    @Override
    public List<CustomerSummary> search(ErpConnection connection, String keyword, int offset, int limit) {
        ErpQuery query = ErpQuery.create()
                .fields(FIELDS)
                .filter(ErpFilter.eq("disabled", 0))
                .orderBy("modified desc")
                .limit(offset, limit);

        String pattern = FrappeSearch.likePattern(keyword);
        if (pattern != null) {
            // Frappe 中 filters 与 or_filters 之间是 AND：已停用客户不会因为 or 条件被带回来。
            query.orFilter(ErpFilter.like("customer_name", pattern))
                    .orFilter(ErpFilter.like("name", pattern))
                    .orFilter(ErpFilter.like("mobile_no", pattern));
        }

        return erpRestClient.list(connection, ErpCustomer.DOCTYPE, query, ErpCustomer.class)
                .stream()
                .map(mapper::toSummary)
                .toList();
    }

    @Override
    public Optional<CustomerSummary> findById(ErpConnection connection, String customerId) {
        return erpRestClient.getDoc(connection, ErpCustomer.DOCTYPE, customerId, ErpCustomer.class)
                .map(mapper::toSummary);
    }
}
