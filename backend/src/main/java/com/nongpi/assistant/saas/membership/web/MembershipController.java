package com.nongpi.assistant.saas.membership.web;

import com.nongpi.assistant.saas.membership.MembershipCommandService;
import com.nongpi.assistant.saas.membership.MembershipRole;
import com.nongpi.assistant.saas.membership.MembershipStatus;
import com.nongpi.assistant.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memberships")
public class MembershipController {

    private final MembershipCommandService membershipCommandService;

    public MembershipController(MembershipCommandService membershipCommandService) {
        this.membershipCommandService = membershipCommandService;
    }

    @GetMapping
    @PreAuthorize("@roles.atLeast('ADMIN')")
    public List<MembershipCommandService.MembershipView> list() {
        return membershipCommandService.listCurrentTenant(SecurityUtils.requireUser().tenantId());
    }

    @PostMapping
    @PreAuthorize("@roles.atLeast('ADMIN')")
    public MembershipCommandService.MembershipView create(@Valid @RequestBody CreateMembershipRequest request) {
        return membershipCommandService.create(SecurityUtils.requireUser(),
                new MembershipCommandService.CreateCommand(
                        request.userId(), request.login(), request.password(), request.displayName(), request.role()));
    }

    @PatchMapping("/{membershipId}")
    @PreAuthorize("@roles.atLeast('ADMIN')")
    public MembershipCommandService.MembershipView update(@PathVariable UUID membershipId,
                                                          @RequestBody UpdateMembershipRequest request) {
        return membershipCommandService.update(SecurityUtils.requireUser(), membershipId,
                new MembershipCommandService.UpdateCommand(request.role(), request.status()));
    }

    public record CreateMembershipRequest(
            UUID userId,
            String login,
            String password,
            String displayName,
            @NotNull MembershipRole role
    ) {
    }

    public record UpdateMembershipRequest(MembershipRole role, MembershipStatus status) {
    }
}
