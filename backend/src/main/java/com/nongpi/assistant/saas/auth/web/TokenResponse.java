package com.nongpi.assistant.saas.auth.web;

import com.nongpi.assistant.saas.membership.MembershipRole;
import com.nongpi.assistant.security.UserPrincipal;

import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        UUID tenantId,
        String tenantName,
        UUID membershipId,
        MembershipRole role,
        String displayName
) {
    public static TokenResponse from(com.nongpi.assistant.saas.auth.AuthService.TokenBundle bundle) {
        UserPrincipal principal = bundle.principal();
        return new TokenResponse(
                bundle.accessToken(),
                bundle.refreshToken(),
                "Bearer",
                bundle.expiresInSeconds(),
                principal.userId(),
                principal.tenantId(),
                principal.tenantName(),
                principal.membershipId(),
                principal.role(),
                principal.displayName()
        );
    }
}
