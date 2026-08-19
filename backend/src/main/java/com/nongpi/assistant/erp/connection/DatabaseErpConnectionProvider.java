package com.nongpi.assistant.erp.connection;

import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.security.crypto.CredentialEncryptionService;
import com.nongpi.assistant.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class DatabaseErpConnectionProvider implements ErpConnectionProvider {

    private final ErpConnectionRepository erpConnectionRepository;
    private final CredentialEncryptionService credentialEncryptionService;

    public DatabaseErpConnectionProvider(ErpConnectionRepository erpConnectionRepository,
                                         CredentialEncryptionService credentialEncryptionService) {
        this.erpConnectionRepository = erpConnectionRepository;
        this.credentialEncryptionService = credentialEncryptionService;
    }

    @Override
    public ErpConnection resolve(TenantContext tenant) {
        UUID tenantId = parseTenantId(tenant.tenantId());
        ErpConnectionEntity entity = erpConnectionRepository.findByTenant_Id(tenantId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ERP_CONNECTION_NOT_CONFIGURED));
        if (entity.getStatus() != ErpConnectionStatus.ACTIVE) {
            throw new BusinessException(BusinessErrorCode.ERP_CONNECTION_NOT_CONFIGURED, "ERP 连接已停用");
        }
        String apiKey = credentialEncryptionService.decrypt(entity.getApiKeyCiphertext());
        String apiSecret = credentialEncryptionService.decrypt(entity.getApiSecretCiphertext());
        return new ErpConnection(
                tenant.tenantId(),
                stripTrailingSlash(entity.getBaseUrl()),
                apiKey,
                apiSecret,
                entity.getSellingPriceList(),
                entity.getDefaultWarehouse(),
                entity.getDefaultCompany(),
                Duration.ofMillis(entity.getConnectTimeoutMs()),
                Duration.ofMillis(entity.getReadTimeoutMs())
        );
    }

    private static UUID parseTenantId(String tenantId) {
        try {
            return UUID.fromString(tenantId);
        } catch (RuntimeException ex) {
            throw new BusinessException(BusinessErrorCode.ERP_CONNECTION_NOT_CONFIGURED);
        }
    }

    public static String stripTrailingSlash(String baseUrl) {
        if (baseUrl == null) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
