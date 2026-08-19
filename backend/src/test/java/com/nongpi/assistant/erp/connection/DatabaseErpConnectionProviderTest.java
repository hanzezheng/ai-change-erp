package com.nongpi.assistant.erp.connection;

import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.saas.membership.MembershipEntity;
import com.nongpi.assistant.saas.membership.MembershipRole;
import com.nongpi.assistant.saas.membership.MembershipStatus;
import com.nongpi.assistant.saas.tenant.TenantEntity;
import com.nongpi.assistant.saas.tenant.TenantStatus;
import com.nongpi.assistant.saas.user.AppUserEntity;
import com.nongpi.assistant.saas.user.UserStatus;
import com.nongpi.assistant.support.AbstractSaasIntegrationTest;
import com.nongpi.assistant.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("数据库 ERP 连接")
class DatabaseErpConnectionProviderTest extends AbstractSaasIntegrationTest {

    @Autowired
    private DatabaseErpConnectionProvider provider;

    @Test
    @DisplayName("按租户解密凭据并规范化 baseUrl")
    void resolvesAndDecrypts() {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        newErpConnection(tenant, "https://erp-a.example.com/", "key-a", "secret-a");

        ErpConnection connection = provider.resolve(new TenantContext(tenant.getId().toString(), tenant.getName()));
        assertThat(connection.baseUrl()).isEqualTo("https://erp-a.example.com");
        assertThat(connection.authorizationHeader()).isEqualTo("token key-a:secret-a");
        assertThat(connection.toString()).doesNotContain("secret-a", "key-a");
    }

    @Test
    @DisplayName("未配置连接时返回 ERP_CONNECTION_NOT_CONFIGURED")
    void missingConnection() {
        TenantEntity tenant = newTenant("未接入 ERP 的租户", TenantStatus.ACTIVE);
        assertThatThrownBy(() -> provider.resolve(new TenantContext(tenant.getId().toString(), tenant.getName())))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).code())
                .isEqualTo(BusinessErrorCode.ERP_CONNECTION_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("ERP 连接 API 不返回明文或密文凭据")
    void apiHidesSecrets() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("admin", "password", UserStatus.ACTIVE);
        MembershipEntity membership = newMembership(tenant, user, MembershipRole.ADMIN, MembershipStatus.ACTIVE);
        newErpConnection(tenant, "https://erp-a.example.com", "key-a", "secret-a");
        String token = accessToken(user, membership);

        mockMvc.perform(get("/api/v1/erp-connection").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseUrl").value("https://erp-a.example.com"))
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.apiSecret").doesNotExist())
                .andExpect(jsonPath("$.apiKeyCiphertext").doesNotExist())
                .andExpect(jsonPath("$.apiSecretCiphertext").doesNotExist())
                .andExpect(jsonPath("$.apiKeyConfigured").value(true));
    }

    @Test
    @DisplayName("Tenant A 不能读取 Tenant B 的 ERP 连接")
    void tenantCannotReadAnotherConnection() throws Exception {
        TenantEntity tenantA = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        TenantEntity tenantB = newTenant("广州批发档口", TenantStatus.ACTIVE);
        AppUserEntity userA = newUser("admin-a", "password", UserStatus.ACTIVE);
        MembershipEntity membershipA = newMembership(tenantA, userA, MembershipRole.ADMIN, MembershipStatus.ACTIVE);
        newErpConnection(tenantA, "https://erp-a.example.com", "key-a", "secret-a");
        newErpConnection(tenantB, "https://erp-b.example.com", "key-b", "secret-b");
        String tokenA = accessToken(userA, membershipA);

        mockMvc.perform(get("/api/v1/erp-connection").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseUrl").value("https://erp-a.example.com"))
                .andExpect(jsonPath("$.baseUrl").value(org.hamcrest.Matchers.not("https://erp-b.example.com")));
    }

    @Test
    @DisplayName("STAFF 不能修改 ERP 连接")
    void staffCannotUpdateConnection() throws Exception {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("staff", "password", UserStatus.ACTIVE);
        MembershipEntity membership = newMembership(tenant, user, MembershipRole.STAFF, MembershipStatus.ACTIVE);
        String token = accessToken(user, membership);

        mockMvc.perform(put("/api/v1/erp-connection")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "baseUrl", "https://erp.example.com",
                                "apiKey", "k",
                                "apiSecret", "s"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("禁止两个 Tenant 绑定同一个 Site")
    void rejectsSharedSite() throws Exception {
        TenantEntity tenantA = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        TenantEntity tenantB = newTenant("广州批发档口", TenantStatus.ACTIVE);
        AppUserEntity userB = newUser("admin-b", "password", UserStatus.ACTIVE);
        MembershipEntity membershipB = newMembership(tenantB, userB, MembershipRole.OWNER, MembershipStatus.ACTIVE);
        newErpConnection(tenantA, "https://erp.example.com", "key-a", "secret-a");
        String tokenB = accessToken(userB, membershipB);

        mockMvc.perform(put("/api/v1/erp-connection")
                        .header("Authorization", bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "baseUrl", "https://erp.example.com",
                                "siteName", tenantA.getName(),
                                "apiKey", "k",
                                "apiSecret", "s"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("未知租户无法解析 ERP 连接")
    void unknownTenant() {
        assertThatThrownBy(() -> provider.resolve(new TenantContext(UUID.randomUUID().toString(), "不存在")))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).code())
                .isEqualTo(BusinessErrorCode.ERP_CONNECTION_NOT_CONFIGURED);
    }
}
