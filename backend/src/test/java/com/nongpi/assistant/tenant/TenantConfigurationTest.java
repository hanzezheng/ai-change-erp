package com.nongpi.assistant.tenant;

import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.config.AppProperties;
import com.nongpi.assistant.erp.connection.ConfiguredErpConnectionProvider;
import com.nongpi.assistant.erp.connection.ErpConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("租户与 ERP 连接配置")
class TenantConfigurationTest {

    private static final AppProperties PROPERTIES = new AppProperties(List.of(
            new AppProperties.Tenant("T001", "徐州水果档口", List.of("token-a", "token-a-2"),
                    new AppProperties.Erp("https://erp-a.example.com/", "key-a", "secret-a",
                            "Standard Selling", "主仓库 - A", Duration.ofSeconds(2), Duration.ofSeconds(5))),
            new AppProperties.Tenant("T002", "广州批发档口", List.of("token-b"),
                    new AppProperties.Erp("https://erp-b.example.com", "key-b", "secret-b",
                            "批发价目表", null, null, null)),
            new AppProperties.Tenant("T003", "未接入 ERP 的租户", List.of("token-c"), null)));

    private final ConfiguredTenantResolver tenantResolver = new ConfiguredTenantResolver(PROPERTIES);
    private final ConfiguredErpConnectionProvider connectionProvider =
            new ConfiguredErpConnectionProvider(PROPERTIES);

    @Test
    @DisplayName("Access Token 解析到对应租户，一个租户可以有多个 Token")
    void resolvesTenantByAccessToken() {
        assertThat(tenantResolver.resolveByAccessToken("token-a"))
                .get().extracting(TenantContext::tenantId).isEqualTo("T001");
        assertThat(tenantResolver.resolveByAccessToken("token-a-2"))
                .get().extracting(TenantContext::tenantId).isEqualTo("T001");
        assertThat(tenantResolver.resolveByAccessToken("token-b"))
                .get().extracting(TenantContext::tenantId).isEqualTo("T002");
    }

    @Test
    @DisplayName("未知或空 Token 解析不出任何租户")
    void rejectsUnknownAccessToken() {
        assertThat(tenantResolver.resolveByAccessToken("伪造的令牌")).isEmpty();
        assertThat(tenantResolver.resolveByAccessToken("")).isEmpty();
        assertThat(tenantResolver.resolveByAccessToken(null)).isEmpty();
    }

    @Test
    @DisplayName("每个租户拿到自己的 ERP 连接与凭据")
    void resolvesPerTenantErpConnection() {
        ErpConnection a = connectionProvider.resolve(new TenantContext("T001", "徐州水果档口"));
        ErpConnection b = connectionProvider.resolve(new TenantContext("T002", "广州批发档口"));

        // 末尾斜杠被规范化，避免拼出 //api/resource
        assertThat(a.baseUrl()).isEqualTo("https://erp-a.example.com");
        assertThat(a.authorizationHeader()).isEqualTo("token key-a:secret-a");
        assertThat(a.sellingPriceList()).isEqualTo("Standard Selling");
        assertThat(a.readTimeout()).isEqualTo(Duration.ofSeconds(5));

        assertThat(b.baseUrl()).isEqualTo("https://erp-b.example.com");
        assertThat(b.authorizationHeader()).isEqualTo("token key-b:secret-b");
        assertThat(b.sellingPriceList()).isEqualTo("批发价目表");
        // 未配置超时时使用默认值，不是 null
        assertThat(b.readTimeout()).isEqualTo(ErpConnection.DEFAULT_READ_TIMEOUT);
        assertThat(b.connectTimeout()).isEqualTo(ErpConnection.DEFAULT_CONNECT_TIMEOUT);

        // 两个租户的连接互不相同，租户之间不会串到同一套 ERPNext
        assertThat(a.baseUrl()).isNotEqualTo(b.baseUrl());
        assertThat(a.clientCacheKey()).isNotEqualTo(b.clientCacheKey());
    }

    @Test
    @DisplayName("租户没有配置 ERP 连接时返回 ERP_UNAVAILABLE")
    void failsWhenTenantHasNoErpConnection() {
        assertThatThrownBy(() -> connectionProvider.resolve(new TenantContext("T003", "未接入 ERP 的租户")))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).code())
                .isEqualTo(BusinessErrorCode.ERP_UNAVAILABLE);
    }

    @Test
    @DisplayName("未知租户拿不到任何 ERP 连接")
    void failsForUnknownTenant() {
        assertThatThrownBy(() -> connectionProvider.resolve(new TenantContext("T999", "不存在的租户")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("没有租户上下文时 require() 直接拒绝")
    void requireFailsWithoutContext() {
        TenantContextHolder.clear();

        assertThatThrownBy(TenantContextHolder::require)
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).code())
                .isEqualTo(BusinessErrorCode.PERMISSION_DENIED);
    }
}
