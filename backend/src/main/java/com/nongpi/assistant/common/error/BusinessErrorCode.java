package com.nongpi.assistant.common.error;

import org.springframework.http.HttpStatus;

/**
 * 统一业务错误码，对应 docs/06_API_DATA_DESIGN.md #66。
 *
 * <p>本轮（ERPNext 主数据读取链路）只登记当前链路真正会产生的错误码。
 * 订单、收款、AI 相关错误码在对应阶段开发时再加入，避免出现永远不会被抛出的死码。
 */
public enum BusinessErrorCode {

    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "客户不存在"),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "商品不存在"),
    INVALID_UOM(HttpStatus.BAD_REQUEST, "单位不在该商品的可用单位范围内"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN, "没有权限执行该操作"),
    TENANT_NOT_FOUND(HttpStatus.FORBIDDEN, "租户不存在或未启用"),
    ERP_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ERP 系统暂时不可用"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    BusinessErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
