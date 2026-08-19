package com.nongpi.assistant.erp.connection;

import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.config.AppProperties;
import com.nongpi.assistant.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ConfiguredErpConnectionProvider implements ErpConnectionProvider {

    private final Map<String, ErpConnection> connectionsByTenantId;

    public ConfiguredErpConnectionProvider(AppProperties properties) {
        Map<String, ErpConnection> index = new HashMap<>();
        for (AppProperties.Tenant tenant : properties.tenants()) {
            AppProperties.Erp erp = tenant.erp();
            if (erp == null) {
                continue;
            }
            index.put(tenant.tenantId(), new ErpConnection(
                    tenant.tenantId(),
                    stripTrailingSlash(erp.baseUrl()),
                    erp.apiKey(),
                    erp.apiSecret(),
                    erp.sellingPriceList(),
                    erp.defaultWarehouse(),
                    erp.connectTimeout(),
                    erp.readTimeout()
            ));
        }
        this.connectionsByTenantId = Map.copyOf(index);
    }

    @Override
    public ErpConnection resolve(TenantContext tenant) {
        ErpConnection connection = connectionsByTenantId.get(tenant.tenantId());
        if (connection == null) {
            throw new BusinessException(BusinessErrorCode.ERP_UNAVAILABLE, "当前租户尚未配置 ERP 连接");
        }
        return connection;
    }

    private static String stripTrailingSlash(String baseUrl) {
        if (baseUrl == null) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
