package com.nongpi.assistant.erp.connection;

import com.nongpi.assistant.tenant.TenantContext;

import java.time.Duration;

/**
 * 按租户解析 ERPNext Site 连接。
 *
 * <p>一个 SaaS Tenant 对应一个 Frappe / ERPNext Site（AGENTS.md #20）。
 * 调用方 Adapter 不直接查询 {@code erp_connection} 表。
 */
public interface ErpConnectionProvider {

    ErpConnection resolve(TenantContext tenant);
}
