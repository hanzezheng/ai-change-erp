package com.nongpi.assistant.tenant;

import com.nongpi.assistant.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 基于服务端配置的租户解析实现。
 *
 * <p>Access Token 只存在于服务端配置，客户端无法凭空构造一个 tenantId 进来。
 */
@Component
public class ConfiguredTenantResolver implements TenantResolver {

    private final Map<String, TenantContext> byAccessToken;
    private final Map<String, TenantContext> byTenantId;

    public ConfiguredTenantResolver(AppProperties properties) {
        Map<String, TenantContext> tokenIndex = new HashMap<>();
        Map<String, TenantContext> idIndex = new HashMap<>();
        for (AppProperties.Tenant tenant : properties.tenants()) {
            TenantContext context = new TenantContext(tenant.tenantId(), tenant.tenantName());
            idIndex.put(tenant.tenantId(), context);
            for (String token : tenant.accessTokens()) {
                tokenIndex.put(token, context);
            }
        }
        this.byAccessToken = Map.copyOf(tokenIndex);
        this.byTenantId = Map.copyOf(idIndex);
    }

    @Override
    public Optional<TenantContext> resolveByAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byAccessToken.get(accessToken));
    }

    @Override
    public Optional<TenantContext> resolveByTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byTenantId.get(tenantId));
    }
}
