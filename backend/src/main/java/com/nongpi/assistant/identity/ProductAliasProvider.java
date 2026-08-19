package com.nongpi.assistant.identity;

import com.nongpi.assistant.tenant.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品简称来源，同样属于 SaaS Product Identity 而非 ERPNext 字段。
 */
public interface ProductAliasProvider {

    Map<String, List<String>> findAliases(TenantContext tenant, Set<String> erpItemCodes);
}
