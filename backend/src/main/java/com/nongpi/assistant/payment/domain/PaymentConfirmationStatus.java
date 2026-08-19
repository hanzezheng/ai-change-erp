package com.nongpi.assistant.payment.domain;

public enum PaymentConfirmationStatus {
    PENDING_CONFIRMATION("待确认"),
    CONFIRMED("已到账"),
    CANCELLED("已取消");

    private final String label;

    PaymentConfirmationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PaymentConfirmationStatus fromDocstatus(int docstatus) {
        if (docstatus == 1) {
            return CONFIRMED;
        }
        if (docstatus == 2) {
            return CANCELLED;
        }
        return PENDING_CONFIRMATION;
    }
}
