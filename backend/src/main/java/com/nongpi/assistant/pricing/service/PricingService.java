package com.nongpi.assistant.pricing.service;

import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.erp.adapter.SalesOrderErpAdapter;
import com.nongpi.assistant.erp.connection.ErpConnectionProvider;
import com.nongpi.assistant.pricing.domain.LastDealPrice;
import com.nongpi.assistant.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PricingService {

    private final SalesOrderErpAdapter salesOrderErpAdapter;
    private final ErpConnectionProvider erpConnectionProvider;

    public PricingService(SalesOrderErpAdapter salesOrderErpAdapter, ErpConnectionProvider erpConnectionProvider) {
        this.salesOrderErpAdapter = salesOrderErpAdapter;
        this.erpConnectionProvider = erpConnectionProvider;
    }

    public LastDealPrice lastDeal(String customerId, String itemCode, String uom) {
        if (customerId == null || customerId.isBlank() || itemCode == null || itemCode.isBlank()
                || uom == null || uom.isBlank()) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "customerId、itemCode、uom 都不能为空");
        }
        return salesOrderErpAdapter.findLastDealPrice(
                        erpConnectionProvider.resolve(TenantContextHolder.require()), customerId, itemCode, uom)
                .orElse(null);
    }
}
