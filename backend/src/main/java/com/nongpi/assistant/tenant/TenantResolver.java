package com.nongpi.assistant.tenant;

import java.util.Optional;

/**
 * 由服务端可信凭据解析租户。
 *
 * <p>Phase 2 引入用户体系后，由 User / Membership 实现替换当前的配置实现，接口不变。
 */
public interface TenantResolver {

    Optional<TenantContext> resolveByAccessToken(String accessToken);

    Optional<TenantContext> resolveByTenantId(String tenantId);
}
