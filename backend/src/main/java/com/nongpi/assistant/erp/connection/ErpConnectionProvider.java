package com.nongpi.assistant.erp.connection;

import com.nongpi.assistant.tenant.TenantContext;

/**
 * 按租户解析 ERP 连接。
 *
 * <p>这一层刻意不表态 ERPNext 是「每租户一套」还是「多租户共享一套」
 * （AGENTS.md #20 要求该决策留待架构冻结）：
 * 不同租户指向不同 baseUrl 即 per-tenant 部署，指向同一 baseUrl 即 shared 部署。
 */
public interface ErpConnectionProvider {

    ErpConnection resolve(TenantContext tenant);
}
