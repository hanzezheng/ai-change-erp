package com.nongpi.assistant.common.error;

import com.nongpi.assistant.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        String traceId = TraceIdFilter.from(request);
        if (ex.code().httpStatus().is5xxServerError()) {
            log.error("[{}] {} {} 业务失败: {}", traceId, request.getMethod(), request.getRequestURI(), ex.code(), ex);
        } else {
            log.warn("[{}] {} {} 业务拒绝: {} - {}", traceId, request.getMethod(), request.getRequestURI(),
                    ex.code(), ex.getMessage());
        }
        return ResponseEntity.status(ex.code().httpStatus())
                .body(ApiErrorResponse.of(ex.code(), ex.getMessage(), traceId, ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        BusinessErrorCode code = BusinessErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiErrorResponse.of(code, code.defaultMessage(), TraceIdFilter.from(request), details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                             HttpServletRequest request) {
        BusinessErrorCode code = BusinessErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiErrorResponse.of(code, code.defaultMessage(), TraceIdFilter.from(request), Map.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        BusinessErrorCode code = BusinessErrorCode.PERMISSION_DENIED;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiErrorResponse.of(code, code.defaultMessage(), TraceIdFilter.from(request), Map.of()));
    }

    /**
     * 兜底分支。任何未被显式映射的异常都不能把内部细节（含 ERPNext 报文）返回给客户端。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = TraceIdFilter.from(request);
        log.error("[{}] {} {} 未预期异常", traceId, request.getMethod(), request.getRequestURI(), ex);
        BusinessErrorCode code = BusinessErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.httpStatus())
                .body(ApiErrorResponse.of(code, code.defaultMessage(), traceId, Map.of()));
    }
}
