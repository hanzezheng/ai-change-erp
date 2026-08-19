package com.nongpi.assistant.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.assistant.common.error.ApiErrorResponse;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 从服务端可信凭据解析租户并写入 {@link TenantContextHolder}。
 *
 * <p>请求头中的 tenantId 一律忽略：租户身份只能来自 Access Token
 * （AGENTS.md #19、docs/06_API_DATA_DESIGN.md #5）。
 *
 * <p>本阶段是临时认证实现。Phase 2 接入正式用户体系后，这里换成
 * Session / JWT + Membership 解析，下游 {@code TenantContextHolder} 用法不变。
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_PATH_PREFIX = "/api/";

    private final TenantResolver tenantResolver;
    private final ObjectMapper objectMapper;

    public TenantContextFilter(TenantResolver tenantResolver, ObjectMapper objectMapper) {
        this.tenantResolver = tenantResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<TenantContext> tenant = extractAccessToken(request).flatMap(tenantResolver::resolveByAccessToken);
        if (tenant.isEmpty()) {
            writeDenied(request, response);
            return;
        }
        TenantContextHolder.set(tenant.get());
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private Optional<String> extractAccessToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private void writeDenied(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        log.warn("[{}] {} {} 拒绝访问：Access Token 缺失或无效", traceId, request.getMethod(), request.getRequestURI());
        BusinessErrorCode code = BusinessErrorCode.PERMISSION_DENIED;
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(code, "Access Token 缺失或无效", traceId, Map.of()));
    }
}
