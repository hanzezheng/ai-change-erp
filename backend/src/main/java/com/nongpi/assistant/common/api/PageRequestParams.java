package com.nongpi.assistant.common.api;

import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;

/**
 * 列表接口的分页参数。page 从 1 开始。
 */
public record PageRequestParams(int page, int pageSize) {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    public static PageRequestParams of(Integer page, Integer pageSize) {
        int resolvedPage = page == null ? 1 : page;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPage < 1) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "page 必须大于等于 1");
        }
        if (resolvedPageSize < 1 || resolvedPageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST,
                    "pageSize 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        return new PageRequestParams(resolvedPage, resolvedPageSize);
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
