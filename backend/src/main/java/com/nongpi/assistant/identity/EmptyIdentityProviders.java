package com.nongpi.assistant.identity;

import com.nongpi.assistant.tenant.TenantContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Identity 模块尚未开发时的空实现。
 *
 * <p>返回空 Map 意味着 API 中的 aliases 一律是空数组。这是刻意的：
 * 宁可先没有称呼，也不能把 ERPNext 的某个字段冒充成「常用称呼」。
 */
@Configuration
public class EmptyIdentityProviders {

    @Bean
    public CustomerAliasProvider customerAliasProvider() {
        return (TenantContext tenant, Set<String> erpCustomerIds) -> Map.of();
    }

    @Bean
    public ProductAliasProvider productAliasProvider() {
        return (TenantContext tenant, Set<String> erpItemCodes) -> Map.of();
    }
}
