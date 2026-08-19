package com.nongpi.assistant.order.domain;

public enum PaymentCollectionStatus {
    UNPAID("未收款"),
    PARTIAL("部分收款"),
    PAID("已收款");

    private final String label;

    PaymentCollectionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
