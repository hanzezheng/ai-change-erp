package com.nongpi.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 服务端租户与 ERP 连接配置。
 *
 * <p>这是本阶段的临时载体：Phase 2 引入 PostgreSQL 后由数据库表接管，
 * {@code TenantResolver} / {@code ErpConnectionProvider} 接口保持不变。
 *
 * <p>ERPNext 多租户部署方式尚未冻结（AGENTS.md #20）。这里既不假设每租户一套 ERPNext，
 * 也不假设共享 ERPNext：每个租户各自携带一份 ERP 连接配置，两种部署都能表达。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(List<Tenant> tenants) {

    public AppProperties {
        tenants = tenants == null ? List.of() : List.copyOf(tenants);
    }

    public record Tenant(
            String tenantId,
            String tenantName,
            List<String> accessTokens,
            Erp erp
    ) {
        public Tenant {
            accessTokens = accessTokens == null ? List.of() : List.copyOf(accessTokens);
        }
    }

    public record Erp(
            String baseUrl,
            String apiKey,
            String apiSecret,
            String sellingPriceList,
            String defaultWarehouse,
            Duration connectTimeout,
            Duration readTimeout
    ) {
    }
}
