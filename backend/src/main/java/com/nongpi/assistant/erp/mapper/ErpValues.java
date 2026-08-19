package com.nongpi.assistant.erp.mapper;

/**
 * ERPNext 字段的通用清洗。
 *
 * <p>ERPNext 对未填写的字段既可能返回 null，也可能返回空串；
 * 部分只读字段（例如 Customer.primary_address）返回带 HTML 换行的文本。
 * 统一在 Adapter 边界处理，业务层只看到干净值或 null。
 */
public final class ErpValues {

    private ErpValues() {
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * ERPNext 的地址快照字段用 {@code <br>} 分隔行，这里压成单行纯文本。
     */
    public static String plainText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.replaceAll("(?i)<br\\s*/?>", ", ")
                .replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .replaceAll("\\s*,\\s*(?=,|$)", "")
                .replaceAll("\\s+", " ");
        return trimToNull(text);
    }

    public static boolean isTruthy(Integer flag) {
        return flag != null && flag != 0;
    }
}
