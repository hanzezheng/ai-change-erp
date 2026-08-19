package com.nongpi.assistant.api;

import com.nongpi.assistant.saas.membership.MembershipEntity;
import com.nongpi.assistant.saas.membership.MembershipRole;
import com.nongpi.assistant.saas.membership.MembershipStatus;
import com.nongpi.assistant.saas.tenant.TenantEntity;
import com.nongpi.assistant.saas.tenant.TenantStatus;
import com.nongpi.assistant.saas.user.AppUserEntity;
import com.nongpi.assistant.saas.user.UserStatus;
import com.nongpi.assistant.support.AbstractSaasIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Membership 权限与最后一个 OWNER 不变量")
class MembershipAuthorizationTest extends AbstractSaasIntegrationTest {

    @Test
    @DisplayName("ADMIN 可以创建 STAFF")
    void adminCanCreateStaff() throws Exception {
        SeededTenant seeded = seedAdminTenant();

        mockMvc.perform(post("/api/v1/memberships")
                        .header("Authorization", bearer(seeded.adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"staff-1","password":"password-1","displayName":"店员","role":"STAFF"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.login").value("staff-1"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("ADMIN 可以创建 ADMIN")
    void adminCanCreateAdmin() throws Exception {
        SeededTenant seeded = seedAdminTenant();

        mockMvc.perform(post("/api/v1/memberships")
                        .header("Authorization", bearer(seeded.adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"admin-2","password":"password-2","role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("ADMIN 创建 OWNER 被拒绝")
    void adminCannotCreateOwner() throws Exception {
        SeededTenant seeded = seedAdminTenant();

        mockMvc.perform(post("/api/v1/memberships")
                        .header("Authorization", bearer(seeded.adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"owner-2","password":"password-2","role":"OWNER"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("ADMIN 不能把 STAFF 提升为 OWNER")
    void adminCannotPromoteStaffToOwner() throws Exception {
        SeededTenant seeded = seedAdminTenant();
        AppUserEntity staffUser = newUser("staff-1", "password", UserStatus.ACTIVE);
        MembershipEntity staff = newMembership(seeded.tenant(), staffUser, MembershipRole.STAFF, MembershipStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/memberships/" + staff.getId())
                        .header("Authorization", bearer(seeded.adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"OWNER"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(membershipRepository.findById(staff.getId()).orElseThrow().getRole())
                .isEqualTo(MembershipRole.STAFF);
    }

    @Test
    @DisplayName("ADMIN 不能修改 OWNER 的角色")
    void adminCannotChangeOwnerRole() throws Exception {
        SeededTenant seeded = seedAdminTenant();

        mockMvc.perform(patch("/api/v1/memberships/" + seeded.ownerMembership().getId())
                        .header("Authorization", bearer(seeded.adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(membershipRepository.findById(seeded.ownerMembership().getId()).orElseThrow().getRole())
                .isEqualTo(MembershipRole.OWNER);
    }

    @Test
    @DisplayName("ADMIN 不能停用 OWNER")
    void adminCannotDisableOwner() throws Exception {
        SeededTenant seeded = seedAdminTenant();

        mockMvc.perform(patch("/api/v1/memberships/" + seeded.ownerMembership().getId())
                        .header("Authorization", bearer(seeded.adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(membershipRepository.findById(seeded.ownerMembership().getId()).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("OWNER 可以创建第二个 OWNER")
    void ownerCanCreateSecondOwner() throws Exception {
        SeededTenant seeded = seedAdminTenant();

        mockMvc.perform(post("/api/v1/memberships")
                        .header("Authorization", bearer(seeded.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"owner-2","password":"password-2","role":"OWNER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));
        assertThat(membershipRepository.countByTenant_IdAndRoleAndStatus(
                seeded.tenant().getId(), MembershipRole.OWNER, MembershipStatus.ACTIVE)).isEqualTo(2);
    }

    @Test
    @DisplayName("只剩一个 ACTIVE OWNER 时不能停用")
    void cannotDisableLastActiveOwner() throws Exception {
        SeededTenant seeded = seedAdminTenant();

        mockMvc.perform(patch("/api/v1/memberships/" + seeded.ownerMembership().getId())
                        .header("Authorization", bearer(seeded.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ACTIVE_OWNER_REQUIRED"));
        assertThat(membershipRepository.findById(seeded.ownerMembership().getId()).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("只剩一个 ACTIVE OWNER 时不能降级")
    void cannotDemoteLastActiveOwner() throws Exception {
        SeededTenant seeded = seedAdminTenant();

        mockMvc.perform(patch("/api/v1/memberships/" + seeded.ownerMembership().getId())
                        .header("Authorization", bearer(seeded.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ACTIVE_OWNER_REQUIRED"));
        assertThat(membershipRepository.findById(seeded.ownerMembership().getId()).orElseThrow().getRole())
                .isEqualTo(MembershipRole.OWNER);
    }

    @Test
    @DisplayName("存在两个 ACTIVE OWNER 时可以降级其中一个")
    void canDemoteOneOfTwoActiveOwners() throws Exception {
        SeededTenant seeded = seedAdminTenant();
        AppUserEntity secondOwnerUser = newUser("owner-2", "password", UserStatus.ACTIVE);
        MembershipEntity secondOwner = newMembership(
                seeded.tenant(), secondOwnerUser, MembershipRole.OWNER, MembershipStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/memberships/" + secondOwner.getId())
                        .header("Authorization", bearer(seeded.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(membershipRepository.countByTenant_IdAndRoleAndStatus(
                seeded.tenant().getId(), MembershipRole.OWNER, MembershipStatus.ACTIVE)).isEqualTo(1);
    }

    @Test
    @DisplayName("OWNER 不能把自己作为最后一个 OWNER 降级")
    void ownerCannotDemoteSelfAsLastOwner() throws Exception {
        SeededTenant seeded = seedAdminTenant();

        mockMvc.perform(patch("/api/v1/memberships/" + seeded.ownerMembership().getId())
                        .header("Authorization", bearer(seeded.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "STAFF"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ACTIVE_OWNER_REQUIRED"));
    }

    @Test
    @DisplayName("创建成员的审计不含密码")
    void membershipAuditDoesNotContainPassword() throws Exception {
        SeededTenant seeded = seedAdminTenant();

        mockMvc.perform(post("/api/v1/memberships")
                        .header("Authorization", bearer(seeded.adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"staff-secret","password":"super-secret-pass","role":"STAFF"}
                                """))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll())
                .allSatisfy(log -> assertThat(String.valueOf(log.getMetadata()))
                        .doesNotContain("super-secret-pass", "password"));
    }

    private SeededTenant seedAdminTenant() {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity owner = newUser("owner", "password", UserStatus.ACTIVE);
        AppUserEntity admin = newUser("admin", "password", UserStatus.ACTIVE);
        MembershipEntity ownerMembership = newMembership(tenant, owner, MembershipRole.OWNER, MembershipStatus.ACTIVE);
        MembershipEntity adminMembership = newMembership(tenant, admin, MembershipRole.ADMIN, MembershipStatus.ACTIVE);
        return new SeededTenant(
                tenant,
                ownerMembership,
                accessToken(owner, ownerMembership),
                accessToken(admin, adminMembership)
        );
    }

    private record SeededTenant(
            TenantEntity tenant,
            MembershipEntity ownerMembership,
            String ownerToken,
            String adminToken
    ) {
    }
}
