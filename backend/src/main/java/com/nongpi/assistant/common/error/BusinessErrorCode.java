package com.nongpi.assistant.common.error;

import org.springframework.http.HttpStatus;

/**
 * 统一业务错误码，对应 docs/06_API_DATA_DESIGN.md #66。
 *
 * <p>订单、收款、AI 相关错误码在对应阶段开发时再加入。
 */
public enum BusinessErrorCode {

    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "客户不存在"),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "商品不存在"),
    INVALID_UOM(HttpStatus.BAD_REQUEST, "单位不在该商品的可用单位范围内"),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "数量必须大于 0"),
    INVALID_RATE(HttpStatus.BAD_REQUEST, "单价不能为负数"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "订单不存在"),
    ORDER_STATUS_INVALID(HttpStatus.CONFLICT, "当前订单状态不允许该操作"),
    ORDER_CONFLICT(HttpStatus.CONFLICT, "订单已被其他人修改，请刷新后重试"),
    ORDER_INVALID(HttpStatus.BAD_REQUEST, "订单数据不合法"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "收款记录不存在"),
    PAYMENT_STATUS_INVALID(HttpStatus.CONFLICT, "当前收款状态不允许该操作"),
    PAYMENT_INVALID(HttpStatus.BAD_REQUEST, "收款数据不合法"),
    PAYMENT_METHOD_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "该付款方式未配置可用账户"),
    ERP_WRITE_CONFIGURATION_INCOMPLETE(HttpStatus.BAD_REQUEST, "当前企业尚未完成 ERP 写入配置"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "幂等键已用于不同的请求"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "相同请求正在处理中"),
    IDEMPOTENCY_OUTCOME_UNKNOWN(HttpStatus.CONFLICT, "上次写入结果未知，禁止自动重试"),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "登录名或密码不正确"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "访问令牌已过期"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "访问令牌无效"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "刷新令牌无效或已失效"),
    USER_DISABLED(HttpStatus.FORBIDDEN, "用户已被停用"),
    TENANT_DISABLED(HttpStatus.FORBIDDEN, "企业已被停用"),
    TENANT_SELECTION_REQUIRED(HttpStatus.CONFLICT, "请选择要进入的企业"),
    MEMBERSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "未找到有效的企业成员关系"),
    LAST_ACTIVE_OWNER_REQUIRED(HttpStatus.CONFLICT, "企业必须至少保留一名有效的所有者"),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN, "没有权限执行该操作"),
    TENANT_NOT_FOUND(HttpStatus.FORBIDDEN, "租户不存在或未启用"),
    ERP_CONNECTION_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "当前企业尚未配置 ERP 连接"),
    ERP_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ERP 系统暂时不可用"),
    ERP_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "ERP 拒绝了该业务数据"),
    UNSUPPORTED_FIELD(HttpStatus.BAD_REQUEST, "当前版本暂不支持该字段"),
    PAYMENT_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "当前版本不支持该收款类型"),
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
