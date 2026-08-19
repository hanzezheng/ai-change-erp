package com.nongpi.assistant.saas.auth;

import com.nongpi.assistant.audit.AuditActions;
import com.nongpi.assistant.audit.AuditService;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.config.AppProperties;
import com.nongpi.assistant.saas.membership.MembershipEntity;
import com.nongpi.assistant.saas.membership.MembershipRepository;
import com.nongpi.assistant.saas.membership.MembershipStatus;
import com.nongpi.assistant.saas.tenant.TenantStatus;
import com.nongpi.assistant.saas.user.AppUserEntity;
import com.nongpi.assistant.saas.user.AppUserRepository;
import com.nongpi.assistant.saas.user.UserStatus;
import com.nongpi.assistant.security.JwtService;
import com.nongpi.assistant.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final AppUserRepository appUserRepository;
    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final AppProperties appProperties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AppUserRepository appUserRepository,
                       MembershipRepository membershipRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService,
                       AppProperties appProperties,
                       Clock clock) {
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Transactional
    public TokenBundle login(String login, String password, UUID requestedTenantId) {
        Optional<AppUserEntity> userOpt = appUserRepository.findByLoginIgnoreCase(login == null ? "" : login.trim());
        if (userOpt.isEmpty() || !passwordEncoder.matches(password == null ? "" : password, userOpt.get().getPasswordHash())) {
            auditService.failure(requestedTenantId, userOpt.map(AppUserEntity::getId).orElse(null),
                    AuditActions.LOGIN_FAILED, "User", login, Map.of("reason", "INVALID_CREDENTIALS"));
            throw new BusinessException(BusinessErrorCode.AUTHENTICATION_FAILED);
        }
        AppUserEntity user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditService.failure(requestedTenantId, user.getId(), AuditActions.LOGIN_FAILED, "User",
                    user.getId().toString(), Map.of("reason", "USER_DISABLED"));
            throw new BusinessException(BusinessErrorCode.USER_DISABLED);
        }

        List<MembershipEntity> memberships = membershipRepository.findByUserIdWithTenant(user.getId());
        MembershipEntity selected = selectMembership(user, memberships, requestedTenantId);
        TokenBundle bundle = issueTokens(user, selected);
        auditService.success(selected.getTenant().getId(), user.getId(), AuditActions.LOGIN_SUCCESS,
                "Tenant", selected.getTenant().getId().toString(), Map.of("membershipId", selected.getId().toString()));
        return bundle;
    }

    @Transactional
    public TokenBundle refresh(String refreshToken) {
        RefreshTokenEntity stored = requireUsableRefreshToken(refreshToken);
        MembershipEntity membership = membershipRepository
                .findByTenant_IdAndUser_Id(stored.getTenant().getId(), stored.getUser().getId())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.REFRESH_TOKEN_INVALID));
        assertMembershipUsable(membership);
        UserPrincipal principal = toPrincipal(stored.getUser(), membership);
        String accessToken = jwtService.issueAccessToken(principal);
        auditService.success(stored.getTenant().getId(), stored.getUser().getId(), AuditActions.REFRESH_TOKEN,
                "RefreshToken", stored.getId().toString(), Map.of());
        return new TokenBundle(accessToken, refreshToken, jwtService.accessTokenTtl().toSeconds(), principal);
    }

    @Transactional
    public void logout(String refreshToken) {
        RefreshTokenEntity stored = refreshTokenRepository.findByTokenHash(hashRefreshToken(refreshToken))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.REFRESH_TOKEN_INVALID));
        if (!stored.isRevoked()) {
            stored.revoke(clock.instant());
        }
        auditService.success(stored.getTenant().getId(), stored.getUser().getId(), AuditActions.LOGOUT,
                "RefreshToken", stored.getId().toString(), Map.of());
    }

    @Transactional
    public TokenBundle switchTenant(UserPrincipal current, UUID targetTenantId) {
        MembershipEntity membership = membershipRepository.findByTenant_IdAndUser_Id(targetTenantId, current.userId())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.PERMISSION_DENIED, "没有该企业的成员关系"));
        assertMembershipUsable(membership);
        AppUserEntity user = membership.getUser();
        TokenBundle bundle = issueTokens(user, membership);
        auditService.success(membership.getTenant().getId(), user.getId(), AuditActions.SWITCH_TENANT,
                "Tenant", membership.getTenant().getId().toString(),
                Map.of("fromTenantId", current.tenantId().toString()));
        return bundle;
    }

    private MembershipEntity selectMembership(AppUserEntity user,
                                              List<MembershipEntity> memberships,
                                              UUID requestedTenantId) {
        if (requestedTenantId != null) {
            MembershipEntity membership = memberships.stream()
                    .filter(item -> item.getTenant().getId().equals(requestedTenantId))
                    .findFirst()
                    .orElse(null);
            if (membership == null) {
                auditService.failure(requestedTenantId, user.getId(), AuditActions.LOGIN_FAILED, "Tenant",
                        requestedTenantId.toString(), Map.of("reason", "NO_MEMBERSHIP"));
                throw new BusinessException(BusinessErrorCode.PERMISSION_DENIED, "没有该企业的成员关系");
            }
            assertMembershipUsable(membership);
            return membership;
        }

        List<MembershipEntity> usable = memberships.stream()
                .filter(item -> item.getStatus() == MembershipStatus.ACTIVE)
                .filter(item -> item.getTenant().getStatus() == TenantStatus.ACTIVE)
                .toList();
        if (usable.size() == 1) {
            return usable.get(0);
        }
        if (usable.size() > 1) {
            List<Map<String, Object>> tenants = usable.stream()
                    .map(item -> Map.<String, Object>of(
                            "tenantId", item.getTenant().getId().toString(),
                            "tenantName", item.getTenant().getName(),
                            "role", item.getRole().name()))
                    .toList();
            throw new BusinessException(BusinessErrorCode.TENANT_SELECTION_REQUIRED,
                    BusinessErrorCode.TENANT_SELECTION_REQUIRED.defaultMessage(),
                    Map.of("tenants", tenants));
        }
        boolean suspendedOnly = memberships.stream()
                .anyMatch(item -> item.getStatus() == MembershipStatus.ACTIVE
                        && item.getTenant().getStatus() == TenantStatus.SUSPENDED);
        if (suspendedOnly) {
            throw new BusinessException(BusinessErrorCode.TENANT_DISABLED);
        }
        throw new BusinessException(BusinessErrorCode.MEMBERSHIP_NOT_FOUND);
    }

    private void assertMembershipUsable(MembershipEntity membership) {
        if (membership.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(BusinessErrorCode.USER_DISABLED);
        }
        if (membership.getTenant().getStatus() != TenantStatus.ACTIVE) {
            throw new BusinessException(BusinessErrorCode.TENANT_DISABLED);
        }
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new BusinessException(BusinessErrorCode.PERMISSION_DENIED, "成员关系已停用");
        }
    }

    private TokenBundle issueTokens(AppUserEntity user, MembershipEntity membership) {
        UserPrincipal principal = toPrincipal(user, membership);
        String accessToken = jwtService.issueAccessToken(principal);
        String refreshToken = newRefreshToken();
        Instant expiresAt = clock.instant().plus(appProperties.jwt().refreshTokenTtl());
        refreshTokenRepository.save(new RefreshTokenEntity(
                UUID.randomUUID(), user, membership.getTenant(), hashRefreshToken(refreshToken), expiresAt));
        return new TokenBundle(accessToken, refreshToken, jwtService.accessTokenTtl().toSeconds(), principal);
    }

    private RefreshTokenEntity requireUsableRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(BusinessErrorCode.REFRESH_TOKEN_INVALID);
        }
        RefreshTokenEntity stored = refreshTokenRepository.findByTokenHash(hashRefreshToken(refreshToken))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.REFRESH_TOKEN_INVALID));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(clock.instant())) {
            throw new BusinessException(BusinessErrorCode.REFRESH_TOKEN_INVALID);
        }
        return stored;
    }

    private UserPrincipal toPrincipal(AppUserEntity user, MembershipEntity membership) {
        return new UserPrincipal(
                user.getId(),
                membership.getTenant().getId(),
                membership.getId(),
                membership.getRole(),
                user.getLogin(),
                user.getDisplayName(),
                membership.getTenant().getName()
        );
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashRefreshToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record TokenBundle(String accessToken, String refreshToken, long expiresInSeconds, UserPrincipal principal) {
    }
}
