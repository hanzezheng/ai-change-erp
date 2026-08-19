package com.nongpi.assistant.erp.client;

import java.util.List;

/**
 * Frappe 列表过滤条件，序列化为 {@code ["field", "operator", value]}。
 */
public record ErpFilter(String field, String operator, Object value) {

    public static ErpFilter eq(String field, Object value) {
        return new ErpFilter(field, "=", value);
    }

    public static ErpFilter in(String field, List<?> values) {
        return new ErpFilter(field, "in", values);
    }

    public static ErpFilter like(String field, String pattern) {
        return new ErpFilter(field, "like", pattern);
    }

    public static ErpFilter isSet(String field) {
        return new ErpFilter(field, "is", "set");
    }

    public List<Object> toJsonArray() {
        return List.of(field, operator, value);
    }
}
