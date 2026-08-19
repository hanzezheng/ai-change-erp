package com.nongpi.assistant.tenant;

/**
 * 服务端解析出来的租户身份。
 *
 * <p>只能由已验证的 JWT Membership 写入 {@link TenantContextHolder}，
 * 禁止用客户端请求体或请求头里的 tenantId 构造（AGENTS.md #19）。
 */
public record TenantContext(String tenantId, String tenantName) {
}
