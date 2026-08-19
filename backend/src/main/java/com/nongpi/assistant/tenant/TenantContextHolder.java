package com.nongpi.assistant.tenant;

import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;

public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 取当前租户。没有租户上下文说明请求绕过了认证链路，直接拒绝而不是回落到默认租户。
     */
    public static TenantContext require() {
        TenantContext context = CURRENT.get();
        if (context == null) {
            throw new BusinessException(BusinessErrorCode.PERMISSION_DENIED, "缺少租户上下文");
        }
        return context;
    }
}
