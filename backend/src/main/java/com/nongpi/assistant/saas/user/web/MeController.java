package com.nongpi.assistant.saas.user.web;

import com.nongpi.assistant.saas.membership.MembershipEntity;
import com.nongpi.assistant.saas.membership.MembershipRepository;
import com.nongpi.assistant.saas.membership.MembershipRole;
import com.nongpi.assistant.saas.membership.MembershipStatus;
import com.nongpi.assistant.saas.tenant.TenantStatus;
import com.nongpi.assistant.security.SecurityUtils;
import com.nongpi.assistant.security.UserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final MembershipRepository membershipRepository;

    public MeController(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @GetMapping("/me")
    public MeResponse me() {
        UserPrincipal principal = SecurityUtils.requireUser();
        List<AvailableTenant> tenants = membershipRepository.findByUserIdWithTenant(principal.userId()).stream()
                .filter(item -> item.getStatus() == MembershipStatus.ACTIVE)
                .filter(item -> item.getTenant().getStatus() == TenantStatus.ACTIVE)
                .map(MeController::toAvailable)
                .toList();
        return new MeResponse(
                principal.userId(),
                principal.displayName(),
                principal.login(),
                principal.tenantId(),
                principal.tenantName(),
                principal.role(),
                tenants
        );
    }

    private static AvailableTenant toAvailable(MembershipEntity membership) {
        return new AvailableTenant(
                membership.getTenant().getId(),
                membership.getTenant().getName(),
                membership.getRole()
        );
    }

    public record MeResponse(
            UUID userId,
            String displayName,
            String login,
            UUID tenantId,
            String tenantName,
            MembershipRole role,
            List<AvailableTenant> availableTenants
    ) {
    }

    public record AvailableTenant(UUID tenantId, String tenantName, MembershipRole role) {
    }
}
