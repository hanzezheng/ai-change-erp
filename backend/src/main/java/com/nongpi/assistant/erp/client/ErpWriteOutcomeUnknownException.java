package com.nongpi.assistant.erp.client;

/**
 * 内部信号：外部写请求可能已经在 ERPNext 落库，但本地无法确认 resourceId。
 *
 * <p>不是公开 API error code。IdempotencyService 必须把它标成 UNKNOWN，
 * 并向客户端返回 {@code IDEMPOTENCY_OUTCOME_UNKNOWN}。
 */
public final class ErpWriteOutcomeUnknownException extends RuntimeException {

    public ErpWriteOutcomeUnknownException(String message) {
        super(message);
    }

    public ErpWriteOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
