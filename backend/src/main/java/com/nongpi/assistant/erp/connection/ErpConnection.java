package com.nongpi.assistant.erp.connection;

import java.time.Duration;

/**
 * 一个租户访问 ERPNext 所需的全部连接信息。
 *
 * <p>凭据只在服务端流转，任何情况下都不返回给客户端
 * （docs/06_API_DATA_DESIGN.md #83）。
 */
public record ErpConnection(
        String tenantId,
        String baseUrl,
        String apiKey,
        String apiSecret,
        String sellingPriceList,
        String defaultWarehouse,
        Duration connectTimeout,
        Duration readTimeout
) {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);

    public ErpConnection {
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
    }

    /**
     * Frappe Token 认证头。
     */
    public String authorizationHeader() {
        return "token " + apiKey + ":" + apiSecret;
    }

    /**
     * 同一租户的连接参数相同即可复用底层 HTTP 客户端。
     * 该键不包含 apiSecret 之外的敏感语义，仅用于进程内缓存。
     */
    public String clientCacheKey() {
        return tenantId + "|" + baseUrl + "|" + connectTimeout.toMillis() + "|" + readTimeout.toMillis();
    }

    @Override
    public String toString() {
        return "ErpConnection[tenantId=" + tenantId + ", baseUrl=" + baseUrl + "]";
    }
}
