package com.nongpi.assistant.saas;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("SaaS 仓储与 Flyway")
class SaasPersistenceTest extends AbstractSaasIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Flyway 迁移后存在 SaaS 核心表，不存在订单/收款/库存事实表")
    void flywayCreatesSaasTablesOnly() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String> tables = jdbc.queryForList(
                "select tablename from pg_tables where schemaname = 'public'", String.class);
        assertThat(tables).contains(
                "tenant", "app_user", "membership", "erp_connection", "refresh_token", "audit_log", "flyway_schema_history");
        assertThat(tables).doesNotContain("orders", "order_items", "payments", "inventory_balance");
    }

    @Test
    @DisplayName("Tenant / User 仓储读写")
    void repositoriesRoundTrip() {
        TenantEntity tenant = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "secret", UserStatus.ACTIVE);
        assertThat(tenantRepository.findById(tenant.getId())).isPresent();
        assertThat(appUserRepository.findByLoginIgnoreCase("BOSS")).get()
                .extracting(AppUserEntity::getId)
                .isEqualTo(user.getId());
    }

    @Test
    @DisplayName("Membership 按租户隔离，A 看不到 B 的成员")
    void membershipIsTenantScoped() throws Exception {
        TenantEntity tenantA = newTenant("徐州水果档口", TenantStatus.ACTIVE);
        TenantEntity tenantB = newTenant("广州批发档口", TenantStatus.ACTIVE);
        AppUserEntity adminA = newUser("admin-a", "password", UserStatus.ACTIVE);
        AppUserEntity staffB = newUser("staff-b", "password", UserStatus.ACTIVE);
        MembershipEntity membershipA = newMembership(tenantA, adminA, MembershipRole.ADMIN, MembershipStatus.ACTIVE);
        newMembership(tenantB, staffB, MembershipRole.STAFF, MembershipStatus.ACTIVE);

        assertThat(membershipRepository.findByTenantIdWithUser(tenantA.getId()))
                .extracting(item -> item.getUser().getLogin())
                .containsExactly("admin-a")
                .doesNotContain("staff-b");

        mockMvc.perform(get("/api/v1/memberships").header("Authorization", bearer(accessToken(adminA, membershipA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].login").value("admin-a"));
    }

    @Test
    @DisplayName("缺少租户上下文时 require() 直接拒绝")
    void requireFailsWithoutContext() {
        com.nongpi.assistant.tenant.TenantContextHolder.clear();
        org.assertj.core.api.Assertions.assertThatThrownBy(com.nongpi.assistant.tenant.TenantContextHolder::require)
                .isInstanceOf(com.nongpi.assistant.common.error.BusinessException.class);
    }
}
