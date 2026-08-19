package com.nongpi.assistant.audit;

public final class AuditActions {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String REFRESH_TOKEN = "REFRESH_TOKEN";
    public static final String LOGOUT = "LOGOUT";
    public static final String SWITCH_TENANT = "SWITCH_TENANT";
    public static final String MEMBERSHIP_CREATE = "MEMBERSHIP_CREATE";
    public static final String MEMBERSHIP_UPDATE = "MEMBERSHIP_UPDATE";
    public static final String ERP_CONNECTION_CREATE = "ERP_CONNECTION_CREATE";
    public static final String ERP_CONNECTION_UPDATE = "ERP_CONNECTION_UPDATE";

    private AuditActions() {
    }
}
