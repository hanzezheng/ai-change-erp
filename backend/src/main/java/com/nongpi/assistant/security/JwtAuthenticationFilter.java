package com.nongpi.assistant.security;

import com.nongpi.assistant.common.error.ApiErrorResponse;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.web.TraceIdFilter;
import com.nongpi.assistant.saas.membership.MembershipEntity;
import com.nongpi.assistant.saas.membership.MembershipRepository;
import com.nongpi.assistant.saas.membership.MembershipStatus;
import com.nongpi.assistant.saas.tenant.TenantStatus;
import com.nongpi.assistant.saas.user.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final MembershipRepository membershipRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   MembershipRepository membershipRepository,
                                   ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.membershipRepository = membershipRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            JwtService.ParsedAccessToken parsed = jwtService.parse(token);
            MembershipEntity membership = membershipRepository.findWithUserAndTenantById(parsed.membershipId())
                    .orElse(null);
            if (membership == null
                    || !membership.getUser().getId().equals(parsed.userId())
                    || !membership.getTenant().getId().equals(parsed.tenantId())) {
                write(request, response, BusinessErrorCode.TOKEN_INVALID);
                return;
            }
            if (membership.getUser().getStatus() != UserStatus.ACTIVE) {
                write(request, response, BusinessErrorCode.USER_DISABLED);
                return;
            }
            if (membership.getTenant().getStatus() != TenantStatus.ACTIVE) {
                write(request, response, BusinessErrorCode.TENANT_DISABLED);
                return;
            }
            if (membership.getStatus() != MembershipStatus.ACTIVE) {
                write(request, response, BusinessErrorCode.PERMISSION_DENIED);
                return;
            }
            UserPrincipal principal = new UserPrincipal(
                    membership.getUser().getId(),
                    membership.getTenant().getId(),
                    membership.getId(),
                    membership.getRole(),
                    membership.getUser().getLogin(),
                    membership.getUser().getDisplayName(),
                    membership.getTenant().getName()
            );
            SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(principal));
            chain.doFilter(request, response);
        } catch (JwtService.TokenExpiredException ex) {
            write(request, response, BusinessErrorCode.TOKEN_EXPIRED);
        } catch (JwtService.TokenInvalidException ex) {
            write(request, response, BusinessErrorCode.TOKEN_INVALID);
        }
    }

    private void write(HttpServletRequest request, HttpServletResponse response, BusinessErrorCode code)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String traceId = TraceIdFilter.from(request);
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(code, code.defaultMessage(), traceId, Map.of()));
    }
}
