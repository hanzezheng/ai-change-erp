package com.nongpi.assistant.identity;

import com.nongpi.assistant.tenant.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户常用称呼来源。
 *
 * <p>称呼属于 SaaS Customer Identity，不是 ERPNext 字段
 * （docs/04_DOMAIN_MODEL.md #21 数据归属表）。Identity 模块尚未开发，
 * 因此这里保留接口，实现返回空集合，不伪造 ERPNext 数据。
 */
public interface CustomerAliasProvider {

    Map<String, List<String>> findAliases(TenantContext tenant, Set<String> erpCustomerIds);
}
