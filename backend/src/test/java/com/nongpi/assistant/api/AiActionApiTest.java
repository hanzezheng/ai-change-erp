package com.nongpi.assistant.api;

import com.nongpi.assistant.ai.client.AiServiceClient;
import com.nongpi.assistant.ai.dto.AiActionResponse;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AI Action 公开入口")
class AiActionApiTest extends AbstractSaasIntegrationTest {

    @MockBean
    AiServiceClient aiServiceClient;

    @Test
    @DisplayName("已登录可调用 /api/v1/ai/actions，转发 Python 结构化结果")
    void actionsReady() throws Exception {
        TenantEntity tenant = newTenant("农批测试档口", TenantStatus.ACTIVE);
        AppUserEntity user = newUser("boss", "correct-password", UserStatus.ACTIVE);
        MembershipEntity membership = newMembership(tenant, user, MembershipRole.OWNER, MembershipStatus.ACTIVE);
        newErpConnection(tenant, "http://127.0.0.1:8000", "k", "s");

        when(aiServiceClient.parseAction(anyMap())).thenReturn(new AiActionResponse(
                "act-1",
                "CREATE_ORDER",
                "READY",
                "ORDER_EDIT",
                Map.of(),
                List.of(),
                Map.of(
                        "customer", Map.of("customerId", "韩兆亮", "customerName", "韩兆亮"),
                        "items", List.of(Map.of("itemCode", "APPLE-80", "qty", 20, "uom", "箱"))
                ),
                null,
                "stub",
                "stub-v0",
                null
        ));

        String token = accessToken(user, membership);

        mockMvc.perform(post("/api/v1/ai/actions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputType":"TEXT",
                                  "text":"老韩80果20箱",
                                  "context":{"currentPage":"HOME","currentItems":[]}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionId").value("act-1"))
                .andExpect(jsonPath("$.actionType").value("CREATE_ORDER"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.payload.items[0].itemCode").value("APPLE-80"));
    }

    @Test
    @DisplayName("未登录不能调用 AI")
    void actionsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/ai/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inputType":"TEXT","text":"老韩80果20箱"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
