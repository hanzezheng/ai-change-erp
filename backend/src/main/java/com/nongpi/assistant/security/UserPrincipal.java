package com.nongpi.assistant.security;

import com.nongpi.assistant.saas.membership.MembershipRole;

import java.util.UUID;

/**
 * 已认证用户在当前 Tenant 下的身份。供授权与 Audit 使用。
 */
public record UserPrincipal(
        UUID userId,
        UUID tenantId,
        UUID membershipId,
        MembershipRole role,
        String login,
        String displayName,
        String tenantName
) {
}
