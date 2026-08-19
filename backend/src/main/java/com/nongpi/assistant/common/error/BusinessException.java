package com.nongpi.assistant.common.error;

import java.util.Map;

/**
 * 面向客户端的业务异常。
 *
 * <p>{@code details} 只允许放已经脱敏的业务字段。ERPNext 原始报文、堆栈和凭据信息
 * 只写服务端日志，不进入该对象（docs/06_API_DATA_DESIGN.md #83）。
 */
public class BusinessException extends RuntimeException {

    private final BusinessErrorCode code;
    private final Map<String, Object> details;

    public BusinessException(BusinessErrorCode code) {
        this(code, code.defaultMessage(), Map.of(), null);
    }

    public BusinessException(BusinessErrorCode code, String message) {
        this(code, message, Map.of(), null);
    }

    public BusinessException(BusinessErrorCode code, String message, Map<String, Object> details) {
        this(code, message, details, null);
    }

    public BusinessException(BusinessErrorCode code, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public BusinessErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
