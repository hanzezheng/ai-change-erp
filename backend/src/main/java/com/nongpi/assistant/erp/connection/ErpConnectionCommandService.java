package com.nongpi.assistant.erp.connection;

import com.nongpi.assistant.audit.AuditActions;
import com.nongpi.assistant.audit.AuditService;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.saas.tenant.TenantEntity;
import com.nongpi.assistant.saas.tenant.TenantRepository;
import com.nongpi.assistant.security.UserPrincipal;
import com.nongpi.assistant.security.crypto.CredentialEncryptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class ErpConnectionCommandService {

    private final ErpConnectionRepository erpConnectionRepository;
    private final TenantRepository tenantRepository;
    private final CredentialEncryptionService credentialEncryptionService;
    private final AuditService auditService;

    public ErpConnectionCommandService(ErpConnectionRepository erpConnectionRepository,
                                       TenantRepository tenantRepository,
                                       CredentialEncryptionService credentialEncryptionService,
                                       AuditService auditService) {
        this.erpConnectionRepository = erpConnectionRepository;
        this.tenantRepository = tenantRepository;
        this.credentialEncryptionService = credentialEncryptionService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ErpConnectionView getForTenant(UUID tenantId) {
        ErpConnectionEntity entity = erpConnectionRepository.findByTenant_Id(tenantId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ERP_CONNECTION_NOT_CONFIGURED));
        return ErpConnectionView.from(entity);
    }

    @Transactional
    public ErpConnectionView upsert(UserPrincipal actor, UpsertCommand command) {
        TenantEntity tenant = tenantRepository.findById(actor.tenantId())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.TENANT_NOT_FOUND));
        String baseUrl = DatabaseErpConnectionProvider.stripTrailingSlash(command.baseUrl());
        String siteName = blankToNull(command.siteName());
        ensureSiteNotShared(baseUrl, siteName, tenant.getId());

        ErpConnectionEntity entity = erpConnectionRepository.findByTenant_Id(tenant.getId())
                .orElseGet(() -> new ErpConnectionEntity(UUID.randomUUID(), tenant));
        boolean creating = entity.getBaseUrl() == null;

        entity.setBaseUrl(baseUrl);
        entity.setSiteName(siteName);
        if (command.apiKey() != null && !command.apiKey().isBlank()) {
            entity.setApiKeyCiphertext(credentialEncryptionService.encrypt(command.apiKey()));
        }
        if (command.apiSecret() != null && !command.apiSecret().isBlank()) {
            entity.setApiSecretCiphertext(credentialEncryptionService.encrypt(command.apiSecret()));
        }
        if (entity.getApiKeyCiphertext() == null || entity.getApiSecretCiphertext() == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "必须提供 ERP API Key 与 Secret");
        }
        entity.setSellingPriceList(blankToNull(command.sellingPriceList()));
        entity.setDefaultWarehouse(blankToNull(command.defaultWarehouse()));
        entity.setStatus(command.status() == null ? ErpConnectionStatus.ACTIVE : command.status());
        entity.setConnectTimeoutMs(command.connectTimeoutMs() == null ? 3000 : command.connectTimeoutMs());
        entity.setReadTimeoutMs(command.readTimeoutMs() == null ? 10000 : command.readTimeoutMs());
        erpConnectionRepository.save(entity);

        auditService.success(actor.tenantId(), actor.userId(),
                creating ? AuditActions.ERP_CONNECTION_CREATE : AuditActions.ERP_CONNECTION_UPDATE,
                "ErpConnection", entity.getId().toString(), Map.of("siteName", siteName == null ? "" : siteName));
        return ErpConnectionView.from(entity);
    }

    private void ensureSiteNotShared(String baseUrl, String siteName, UUID tenantId) {
        erpConnectionRepository.findConflictingSite(baseUrl, siteName, tenantId).ifPresent(existing -> {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST,
                    "该 ERPNext Site 已绑定其他企业，禁止多 Tenant 共用同一个 Site");
        });
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record UpsertCommand(
            String baseUrl,
            String siteName,
            String apiKey,
            String apiSecret,
            String sellingPriceList,
            String defaultWarehouse,
            ErpConnectionStatus status,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) {
    }

    public record ErpConnectionView(
            UUID id,
            UUID tenantId,
            String baseUrl,
            String siteName,
            String sellingPriceList,
            String defaultWarehouse,
            ErpConnectionStatus status,
            int connectTimeoutMs,
            int readTimeoutMs,
            boolean apiKeyConfigured,
            boolean apiSecretConfigured
    ) {
        static ErpConnectionView from(ErpConnectionEntity entity) {
            return new ErpConnectionView(
                    entity.getId(),
                    entity.getTenant().getId(),
                    entity.getBaseUrl(),
                    entity.getSiteName(),
                    entity.getSellingPriceList(),
                    entity.getDefaultWarehouse(),
                    entity.getStatus(),
                    entity.getConnectTimeoutMs(),
                    entity.getReadTimeoutMs(),
                    entity.getApiKeyCiphertext() != null && !entity.getApiKeyCiphertext().isBlank(),
                    entity.getApiSecretCiphertext() != null && !entity.getApiSecretCiphertext().isBlank()
            );
        }
    }
}
