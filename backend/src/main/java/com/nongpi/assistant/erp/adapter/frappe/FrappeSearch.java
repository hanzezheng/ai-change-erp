package com.nongpi.assistant.erp.adapter.frappe;

/**
 * 搜索关键字到 Frappe LIKE 表达式的转换。
 */
final class FrappeSearch {

    private FrappeSearch() {
    }

    /**
     * 关键字里的 {@code %} 和 {@code _} 会被 SQL LIKE 当成通配符，
     * 用户输入的这两个字符必须转义成字面量，否则搜索结果不可预期。
     *
     * @return 关键字为空时返回 null，表示不加搜索条件
     */
    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
