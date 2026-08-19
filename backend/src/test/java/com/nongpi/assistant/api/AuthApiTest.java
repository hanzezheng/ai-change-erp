package com.nongpi.assistant.api;

import com.nongpi.assistant.audit.AuditActions;
import com.nongpi.assistant.audit.AuditLogEntity;
import com.nongpi.assistant.audit.AuditResult;
import com.nongpi.assistant.saas.auth.AuthService;
import com.nongpi.assistant.saas.membership.MembershipEntity;
import com.nongpi.assistant.saas.membership.MembershipRole;
import com.nongpi.assistant.saas.membership.MembershipStatus;
import com.nongpi.assistant.saas.tenant.TenantEntity;
import com.nongpi.assistant.saas.tenant.TenantStatus;
import com.nongpi.assistant.saas.user.AppUserEntity;
import com.nongpi.assistant.saas.user.UserStatus;
import com.nongpi.assistant.security.UserPrincipal;
import com.nongpi.assistant.support.AbstractSaasIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("认证与租户切换")
class AuthApiTest extends AbstractSaasIntegrationTest {

    @Test
    @DisplayName("登录成功返回 JWT，不返回密码，并写 LOGIN_SUCCESS 审计")
    void loginSuccess() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        newMembership(tenant, user, MembershipRole.OWNER, MembershipStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"boss","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.tenantId").value(tenant.getId().toString()))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(auditLogRepository.findByActionOrderByCreatedAtDesc(AuditActions.LOGIN_SUCCESS))
                .extracting(AuditLogEntity::getResult)
                .containsExactly(AuditResult.SUCCESS);
        assertThat(refreshTokenRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll().get(0).getTokenHash()).hasSize(64);
    }

    @Test
    @DisplayName("错误密码返回 AUTHENTICATION_FAILED，审计不含密码")
    void loginWrongPassword() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        newMembership(tenant, user, MembershipRole.OWNER, MembershipStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"boss","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

        AuditLogEntity log = auditLogRepository.findByActionOrderByCreatedAtDesc(AuditActions.LOGIN_FAILED).get(0);
        assertThat(log.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(String.valueOf(log.getMetadata())).doesNotContain("wrong", "correct-password");
    }

    @Test
    @DisplayName("停用用户无法登录")
    void loginDisabledUser() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.DISABLED);
        newMembership(tenant, user, MembershipRole.OWNER, MembershipStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"boss","password":"correct-password"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));
    }

    @Test
    @DisplayName("停用租户无法登录")
    void loginDisabledTenant() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.SUSPENDED);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        newMembership(tenant, user, MembershipRole.OWNER, MembershipStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"boss","password":"correct-password"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_DISABLED"));
    }

    @Test
    @DisplayName("单租户用户登录可不传 tenantId")
    void loginAutoSelectsSingleTenant() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        newMembership(tenant, user, MembershipRole.STAFF, MembershipStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"boss","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantName").value("徐州水果档口"));
    }

    @Test
    @DisplayName("多租户未选择时返回 TENANT_SELECTION_REQUIRED 和可选企业列表")
    void loginRequiresTenantSelection() throws Exception {
        TenantEntity t1 = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        TenantEntity t2 = newTenant("广州批发档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        newMembership(t1, user, MembershipRole.OWNER, MembershipStatus.ACTIVE);
        newMembership(t2, user, MembershipRole.STAFF, MembershipStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"boss","password":"correct-password"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_SELECTION_REQUIRED"))
                .andExpect(jsonPath("$.details.tenants.length()").value(2));
    }

    @Test
    @DisplayName("选择没有 Membership 的租户返回 PERMISSION_DENIED")
    void loginRejectsTenantWithoutMembership() throws Exception {
        TenantEntity own = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        TenantEntity other = newTenant("广州批发档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        newMembership(own, user, MembershipRole.OWNER, MembershipStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "login", "boss",
                                "password", "correct-password",
                                "tenantId", other.getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("有效 JWT 可以访问受保护接口")
    void validJwt() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        MembershipEntity membership = newMembership(tenant, user, MembershipRole.STAFF, MembershipStatus.ACTIVE);
        String token = accessToken(user, membership);

        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.tenantId").value(tenant.getId().toString()));
    }

    @Test
    @DisplayName("GET /me 返回当前用户与租户，不含 ERP 凭据")
    void meReturnsCurrentUser() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        MembershipEntity membership = newMembership(tenant, user, MembershipRole.ADMIN, MembershipStatus.ACTIVE);
        String token = accessToken(user, membership);

        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.tenantName").value("徐州水果档口"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.availableTenants[0].tenantId").value(tenant.getId().toString()))
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.apiSecret").doesNotExist());
    }

    @Test
    @DisplayName("过期 JWT 返回 TOKEN_EXPIRED")
    void expiredJwt() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        MembershipEntity membership = newMembership(tenant, user, MembershipRole.STAFF, MembershipStatus.ACTIVE);
        UserPrincipal principal = new UserPrincipal(user.getId(), tenant.getId(), membership.getId(),
                membership.getRole(), user.getLogin(), user.getDisplayName(), tenant.getName());
        String expired = jwtService.issueAccessToken(principal, Instant.now().minusSeconds(60));

        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("篡改 JWT 返回 TOKEN_INVALID")
    void tamperedJwt() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        MembershipEntity membership = newMembership(tenant, user, MembershipRole.STAFF, MembershipStatus.ACTIVE);
        String token = accessToken(user, membership) + "tamper";

        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Refresh Token 可换发新的 Access Token，原始 refresh 不入库")
    void refreshToken() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        newMembership(tenant, user, MembershipRole.STAFF, MembershipStatus.ACTIVE);

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"boss","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refresh = objectMapper.readTree(body).get("refreshToken").asText();

        assertThat(refreshTokenRepository.findAll().get(0).getTokenHash())
                .isEqualTo(AuthService.hashRefreshToken(refresh));
        assertThat(refreshTokenRepository.findAll().get(0).getTokenHash()).isNotEqualTo(refresh);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").value(refresh));
    }

    @Test
    @DisplayName("已撤销的 Refresh Token 不能再换发 Access Token")
    void revokedRefreshToken() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        newMembership(tenant, user, MembershipRole.STAFF, MembershipStatus.ACTIVE);

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"boss","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refresh = objectMapper.readTree(loginBody).get("refreshToken").asText();

        var stored = refreshTokenRepository.findAll().get(0);
        stored.revoke(Instant.now());
        refreshTokenRepository.save(stored);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Logout 后 Refresh Token 失效")
    void logoutRevokesRefreshToken() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        MembershipEntity membership = newMembership(tenant, user, MembershipRole.STAFF, MembershipStatus.ACTIVE);
        String access = accessToken(user, membership);

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"boss","password":"correct-password"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String refresh = objectMapper.readTree(loginBody).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("切换到有 Membership 的租户会签发新 Token")
    void switchTenant() throws Exception {
        TenantEntity t1 = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        TenantEntity t2 = newTenant("广州批发档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        MembershipEntity m1 = newMembership(t1, user, MembershipRole.OWNER, MembershipStatus.ACTIVE);
        newMembership(t2, user, MembershipRole.STAFF, MembershipStatus.ACTIVE);
        String token = accessToken(user, m1);

        mockMvc.perform(post("/api/v1/auth/switch-tenant")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tenantId", t2.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(t2.getId().toString()))
                .andExpect(jsonPath("$.role").value("STAFF"));
    }

    @Test
    @DisplayName("切换到没有 Membership 的租户返回 PERMISSION_DENIED")
    void switchTenantDenied() throws Exception {
        TenantEntity t1 = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        TenantEntity t2 = newTenant("广州批发档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        MembershipEntity m1 = newMembership(t1, user, MembershipRole.OWNER, MembershipStatus.ACTIVE);
        String token = accessToken(user, m1);

        mockMvc.perform(post("/api/v1/auth/switch-tenant")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tenantId", t2.getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("BCrypt 校验正确密码，拒绝错误密码")
    void bcryptPasswordVerification() {
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        assertThat(passwordEncoder.matches("correct-password", user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("wrong", user.getPasswordHash())).isFalse();
        assertThat(user.getPasswordHash()).startsWith("$2");
    }
}
