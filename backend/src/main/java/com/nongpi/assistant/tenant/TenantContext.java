package com.nongpi.assistant.tenant;

/**
 * 服务端解析出来的租户身份。
 *
 * <p>只能由 {@link TenantResolver} 从服务端可信凭据推导，
 * 禁止用客户端请求体或请求头里的 tenantId 构造（AGENTS.md #19）。
 */
public record TenantContext(String tenantId, String tenantName) {
}
