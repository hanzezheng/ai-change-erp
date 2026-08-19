package com.nongpi.assistant.erp.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Frappe 列表查询参数。
 *
 * <p>{@code parent} 用于直接查询子表 DocType（例如以 {@code parent=Item} 查询
 * {@code UOM Conversion Detail}），从而一次批量拿到多个 Item 的子表行，
 * 避免逐个 Item 拉完整文档造成的 N+1。
 */
public final class ErpQuery {

    private final List<String> fields = new ArrayList<>();
    private final List<ErpFilter> filters = new ArrayList<>();
    private final List<ErpFilter> orFilters = new ArrayList<>();
    private String orderBy;
    private Integer limitStart;
    private Integer limitPageLength;
    private String parent;

    public static ErpQuery create() {
        return new ErpQuery();
    }

    public ErpQuery fields(String... names) {
        this.fields.addAll(List.of(names));
        return this;
    }

    public ErpQuery filter(ErpFilter filter) {
        this.filters.add(filter);
        return this;
    }

    public ErpQuery orFilter(ErpFilter filter) {
        this.orFilters.add(filter);
        return this;
    }

    public ErpQuery orderBy(String orderBy) {
        this.orderBy = orderBy;
        return this;
    }

    public ErpQuery limit(int start, int pageLength) {
        this.limitStart = start;
        this.limitPageLength = pageLength;
        return this;
    }

    /**
     * 取全部结果。Frappe 中 {@code limit_page_length=0} 表示不分页。
     * 只用于按主键批量取子表这类结果集受控的查询。
     */
    public ErpQuery unlimited() {
        this.limitStart = 0;
        this.limitPageLength = 0;
        return this;
    }

    public ErpQuery parent(String parentDoctype) {
        this.parent = parentDoctype;
        return this;
    }

    public List<String> getFields() {
        return List.copyOf(fields);
    }

    public List<ErpFilter> getFilters() {
        return List.copyOf(filters);
    }

    public List<ErpFilter> getOrFilters() {
        return List.copyOf(orFilters);
    }

    public String getOrderBy() {
        return orderBy;
    }

    public Integer getLimitStart() {
        return limitStart;
    }

    public Integer getLimitPageLength() {
        return limitPageLength;
    }

    public String getParent() {
        return parent;
    }
}
