package com.nongpi.assistant.erp.mapper;

import com.nongpi.assistant.customer.domain.CustomerSummary;
import com.nongpi.assistant.erp.dto.ErpCustomer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ERPNext Customer → App Customer 映射（docs/06_API_DATA_DESIGN.md #70）。
 */
@Component
public class CustomerErpMapper {

    /**
     * {@code aliases} 在这里固定为空：称呼属于 SaaS Customer Identity，
     * 由上层 Service 叠加，Adapter 不得从 ERPNext 字段凑一个出来。
     */
    public CustomerSummary toSummary(ErpCustomer source) {
        String customerId = ErpValues.trimToNull(source.name());
        String customerName = ErpValues.trimToNull(source.customerName());
        return new CustomerSummary(
                customerId,
                customerName != null ? customerName : customerId,
                List.of(),
                ErpValues.trimToNull(source.mobileNo()),
                ErpValues.plainText(source.primaryAddress())
        );
    }
}
