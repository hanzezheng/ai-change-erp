package com.nongpi.assistant.saas.membership;

import com.nongpi.assistant.audit.AuditActions;
import com.nongpi.assistant.audit.AuditService;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.saas.tenant.TenantEntity;
import com.nongpi.assistant.saas.tenant.TenantRepository;
import com.nongpi.assistant.saas.user.AppUserEntity;
import com.nongpi.assistant.saas.user.AppUserRepository;
import com.nongpi.assistant.saas.user.UserStatus;
import com.nongpi.assistant.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MembershipCommandService {

    private final MembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public MembershipCommandService(MembershipRepository membershipRepository,
                                    TenantRepository tenantRepository,
                                    AppUserRepository appUserRepository,
                                    PasswordEncoder passwordEncoder,
                                    AuditService auditService) {
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<MembershipView> listCurrentTenant(UserPrincipal actor) {
        assertCanManageMemberships(actor);
        return membershipRepository.findByTenantIdWithUser(actor.tenantId()).stream()
                .map(MembershipView::from)
                .toList();
    }

    @Transactional
    public MembershipView create(UserPrincipal actor, CreateCommand command) {
        assertCanManageMemberships(actor);
        if (command.role() == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "必须指定成员角色");
        }
        assertCanAssignRole(actor, command.role());
        TenantEntity tenant = lockTenant(actor.tenantId());
        AppUserEntity user = resolveOrCreateUser(command);
        if (membershipRepository.existsByTenant_IdAndUser_Id(tenant.getId(), user.getId())) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "该用户已属于当前企业");
        }
        MembershipEntity membership = new MembershipEntity(
                UUID.randomUUID(), tenant, user, command.role(), MembershipStatus.ACTIVE);
        membershipRepository.save(membership);
        auditService.success(actor.tenantId(), actor.userId(), AuditActions.MEMBERSHIP_CREATE,
                "Membership", membership.getId().toString(),
                Map.of("role", command.role().name(), "userId", user.getId().toString()));
        return MembershipView.from(membership);
    }

    @Transactional
    public MembershipView update(UserPrincipal actor, UUID membershipId, UpdateCommand command) {
        assertCanManageMemberships(actor);
        TenantEntity tenant = lockTenant(actor.tenantId());
        MembershipEntity membership = membershipRepository.findWithUserAndTenantById(membershipId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.MEMBERSHIP_NOT_FOUND));
        if (!membership.getTenant().getId().equals(tenant.getId())) {
            throw new BusinessException(BusinessErrorCode.PERMISSION_DENIED);
        }
        assertCanMutateMembership(actor, membership);

        MembershipRole newRole = command.role() == null ? membership.getRole() : command.role();
        MembershipStatus newStatus = command.status() == null ? membership.getStatus() : command.status();
        if (command.role() != null) {
            assertCanAssignRole(actor, command.role());
        }
        assertKeepsLastActiveOwner(tenant.getId(), membership, newRole, newStatus);

        if (command.role() != null) {
            membership.setRole(command.role());
        }
        if (command.status() != null) {
            membership.setStatus(command.status());
        }
        auditService.success(actor.tenantId(), actor.userId(), AuditActions.MEMBERSHIP_UPDATE,
                "Membership", membership.getId().toString(),
                Map.of("role", membership.getRole().name(), "status", membership.getStatus().name()));
        return MembershipView.from(membership);
    }

    private TenantEntity lockTenant(UUID tenantId) {
        return tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.TENANT_NOT_FOUND));
    }

    private void assertCanManageMemberships(UserPrincipal actor) {
        if (actor == null || actor.role() == null || !actor.role().atLeast(MembershipRole.ADMIN)) {
            throw new BusinessException(BusinessErrorCode.PERMISSION_DENIED);
        }
    }

    private void assertCanAssignRole(UserPrincipal actor, MembershipRole targetRole) {
        if (actor.role() == MembershipRole.OWNER) {
            return;
        }
        if (actor.role() == MembershipRole.ADMIN
                && (targetRole == MembershipRole.ADMIN || targetRole == MembershipRole.STAFF)) {
            return;
        }
        throw new BusinessException(BusinessErrorCode.PERMISSION_DENIED, "不能授予所有者角色");
    }

    private void assertCanMutateMembership(UserPrincipal actor, MembershipEntity target) {
        if (actor.role() == MembershipRole.OWNER) {
            return;
        }
        if (actor.role() == MembershipRole.ADMIN && target.getRole() != MembershipRole.OWNER) {
            return;
        }
        throw new BusinessException(BusinessErrorCode.PERMISSION_DENIED, "不能修改所有者成员关系");
    }

    /**
     * 每个 ACTIVE Tenant 必须至少保留一名 ACTIVE OWNER。
     * 调用前必须已锁住 Tenant 行，保证检查与更新在同一事务中串行。
     */
    private void assertKeepsLastActiveOwner(UUID tenantId,
                                            MembershipEntity target,
                                            MembershipRole newRole,
                                            MembershipStatus newStatus) {
        boolean currentlyActiveOwner = target.getRole() == MembershipRole.OWNER
                && target.getStatus() == MembershipStatus.ACTIVE;
        boolean remainsActiveOwner = newRole == MembershipRole.OWNER
                && newStatus == MembershipStatus.ACTIVE;
        if (!currentlyActiveOwner || remainsActiveOwner) {
            return;
        }
        long activeOwners = membershipRepository.countByTenant_IdAndRoleAndStatus(
                tenantId, MembershipRole.OWNER, MembershipStatus.ACTIVE);
        if (activeOwners <= 1) {
            throw new BusinessException(BusinessErrorCode.LAST_ACTIVE_OWNER_REQUIRED);
        }
    }

    private AppUserEntity resolveOrCreateUser(CreateCommand command) {
        if (command.userId() != null) {
            return appUserRepository.findById(command.userId())
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.INVALID_REQUEST, "用户不存在"));
        }
        if (command.login() == null || command.login().isBlank()) {
            throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "必须提供 userId 或 login");
        }
        return appUserRepository.findByLoginIgnoreCase(command.login().trim())
                .orElseGet(() -> {
                    if (command.password() == null || command.password().isBlank()) {
                        throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "新建用户必须设置密码");
                    }
                    String displayName = command.displayName() == null || command.displayName().isBlank()
                            ? command.login().trim()
                            : command.displayName().trim();
                    AppUserEntity created = new AppUserEntity(
                            UUID.randomUUID(),
                            command.login().trim(),
                            passwordEncoder.encode(command.password()),
                            displayName,
                            UserStatus.ACTIVE
                    );
                    return appUserRepository.save(created);
                });
    }

    public record CreateCommand(UUID userId, String login, String password, String displayName, MembershipRole role) {
    }

    public record UpdateCommand(MembershipRole role, MembershipStatus status) {
    }

    public record MembershipView(UUID membershipId, UUID userId, String login, String displayName,
                                 MembershipRole role, MembershipStatus status) {
        static MembershipView from(MembershipEntity entity) {
            return new MembershipView(
                    entity.getId(),
                    entity.getUser().getId(),
                    entity.getUser().getLogin(),
                    entity.getUser().getDisplayName(),
                    entity.getRole(),
                    entity.getStatus()
            );
        }
    }
}
