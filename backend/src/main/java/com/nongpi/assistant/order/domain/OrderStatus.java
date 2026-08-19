package com.nongpi.assistant.order.domain;

public enum OrderStatus {
    DRAFT("草稿"),
    SUBMITTED("已提交"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static OrderStatus fromErp(int docstatus, String erpStatus) {
        if (docstatus == 0) {
            return DRAFT;
        }
        if (docstatus == 2) {
            return CANCELLED;
        }
        if (docstatus == 1 && "Completed".equalsIgnoreCase(erpStatus)) {
            return COMPLETED;
        }
        return SUBMITTED;
    }
}
