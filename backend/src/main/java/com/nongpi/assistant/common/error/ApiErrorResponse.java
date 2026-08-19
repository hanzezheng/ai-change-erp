package com.nongpi.assistant.common.error;

import java.util.Map;

/**
 * 统一错误响应体，对应 docs/06_API_DATA_DESIGN.md #65。
 */
public record ApiErrorResponse(
        String code,
        String message,
        String traceId,
        Map<String, Object> details
) {
    public static ApiErrorResponse of(BusinessErrorCode code, String message, String traceId, Map<String, Object> details) {
        return new ApiErrorResponse(code.name(), message, traceId, details == null ? Map.of() : details);
    }
}
