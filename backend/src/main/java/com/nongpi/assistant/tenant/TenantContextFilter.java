package com.nongpi.assistant.tenant;

import com.nongpi.assistant.security.UserAuthentication;
import com.nongpi.assistant.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从已验证的 Spring Security 身份写入 {@link TenantContextHolder}。
 * 现有 Customer / Product / Inventory Service 继续只依赖 Holder，不感知 JWT。
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof UserAuthentication userAuthentication) {
            UserPrincipal principal = userAuthentication.getPrincipal();
            TenantContextHolder.set(new TenantContext(
                    principal.tenantId().toString(),
                    principal.tenantName()
            ));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
