package com.nongpi.assistant.common.api;

import java.util.List;

/**
 * 传统分页响应（docs/06_API_DATA_DESIGN.md #80）。
 *
 * <p>ERPNext 的列表接口不返回总数，额外查一次 count 会让每个列表请求翻倍。
 * 因此这里不提供 totalCount，只提供 {@code hasMore}，由多取一条判断。
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int pageSize,
        boolean hasMore
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int pageSize, boolean hasMore) {
        return new PageResponse<>(content, page, pageSize, hasMore);
    }
}
