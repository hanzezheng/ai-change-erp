package com.nongpi.assistant.erp.client;

import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

/**
 * 把 ERPNext / 传输层异常翻译成统一业务错误。
 *
 * <p>ERPNext 的响应体、堆栈和 URL 只写服务端日志，永远不进入返回给客户端的
 * {@link BusinessException}（docs/06_API_DATA_DESIGN.md #83）。
 */
public final class ErpErrorTranslator {

    private static final Logger log = LoggerFactory.getLogger(ErpErrorTranslator.class);

    private ErpErrorTranslator() {
    }

    public static BusinessException translate(String doctype, Throwable cause) {
        if (cause instanceof BusinessException businessException) {
            return businessException;
        }
        if (cause instanceof ResourceAccessException) {
            log.error("ERPNext 连接失败, doctype={}", doctype, cause);
            return erpUnavailable(cause);
        }
        if (cause instanceof HttpStatusCodeException httpException) {
            return translateStatus(doctype, httpException);
        }
        log.error("ERPNext 调用出现未预期异常, doctype={}", doctype, cause);
        return erpUnavailable(cause);
    }

    private static BusinessException translateStatus(String doctype, HttpStatusCodeException cause) {
        int status = cause.getStatusCode().value();
        String responseBody = cause.getResponseBodyAsString();
        log.error("ERPNext 返回错误状态, doctype={}, status={}, body={}", doctype, status, responseBody, cause);
        if (isTimestampMismatch(responseBody)) {
            return new BusinessException(BusinessErrorCode.ORDER_CONFLICT,
                    BusinessErrorCode.ORDER_CONFLICT.defaultMessage(), java.util.Map.of(), cause);
        }
        if (status == 403) {
            return new BusinessException(BusinessErrorCode.PERMISSION_DENIED,
                    "ERP 拒绝了该操作", java.util.Map.of(), cause);
        }
        if (isClientValidation(status, responseBody)) {
            return new BusinessException(BusinessErrorCode.ERP_VALIDATION_FAILED,
                    BusinessErrorCode.ERP_VALIDATION_FAILED.defaultMessage(), java.util.Map.of(), cause);
        }
        return erpUnavailable(cause);
    }

    private static boolean isTimestampMismatch(String body) {
        return body != null && body.contains("TimestampMismatchError");
    }

    private static boolean isClientValidation(int status, String body) {
        if (status == 400 || status == 409 || status == 417 || status == 422) {
            return true;
        }
        return body != null && (body.contains("ValidationError") || body.contains("frappe.exceptions"));
    }

    private static BusinessException erpUnavailable(Throwable cause) {
        return new BusinessException(BusinessErrorCode.ERP_UNAVAILABLE,
                BusinessErrorCode.ERP_UNAVAILABLE.defaultMessage(), java.util.Map.of(), cause);
    }
}
