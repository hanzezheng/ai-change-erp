package com.nongpi.assistant.erp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.erp.dto.ErpCustomer;
import com.nongpi.assistant.erp.support.FakeErpNext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ERPNext 错误转换")
class ErpErrorTranslationTest {

    private FakeErpNext erp;
    private ErpRestClient client;

    @BeforeEach
    void setUp() throws IOException {
        erp = new FakeErpNext();
        erp.start();
        client = new ErpRestClient(new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        erp.close();
    }

    @ParameterizedTest(name = "ERPNext 返回 {0} 时映射为 {1}")
    @CsvSource({
            "401, ERP_UNAVAILABLE",
            "403, PERMISSION_DENIED",
            "417, ERP_VALIDATION_FAILED",
            "500, ERP_UNAVAILABLE",
            "502, ERP_UNAVAILABLE",
            "503, ERP_UNAVAILABLE"
    })
    void mapsHttpStatusToBusinessErrorCode(int status, BusinessErrorCode expected) {
        erp.onListStatus(ErpCustomer.DOCTYPE, status, "{\"exception\": \"内部细节\"}");

        assertThatThrownBy(() -> client.list(erp.connection("T001"), ErpCustomer.DOCTYPE,
                ErpQuery.create().fields("name"), ErpCustomer.class))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("ERPNext ValidationError 映射为 ERP_VALIDATION_FAILED，不告诉客户端 ERP 不可用")
    void mapsValidationErrorToErpValidationFailed() {
        erp.onListStatus(ErpCustomer.DOCTYPE, 417,
                "{\"exc_type\":\"ValidationError\",\"exception\":\"frappe.exceptions.ValidationError: Allocated Amount cannot be greater than outstanding amount.\"}");

        BusinessException exception = catchBusinessException();

        assertThat(exception.code()).isEqualTo(BusinessErrorCode.ERP_VALIDATION_FAILED);
        assertThat(exception.getMessage()).isEqualTo(BusinessErrorCode.ERP_VALIDATION_FAILED.defaultMessage());
        assertThat(exception.getMessage()).doesNotContain("Allocated Amount", "traceback", "frappe.exceptions");
        assertThat(exception.details()).isEmpty();
    }

    @Test
    @DisplayName("TimestampMismatchError 仍映射为 ORDER_CONFLICT")
    void mapsTimestampMismatchToOrderConflict() {
        erp.onListStatus(ErpCustomer.DOCTYPE, 417,
                "{\"exc_type\":\"TimestampMismatchError\",\"exception\":\"frappe.exceptions.TimestampMismatchError: Document has been modified after you have opened it\"}");

        assertThatThrownBy(() -> client.list(erp.connection("T001"), ErpCustomer.DOCTYPE,
                ErpQuery.create().fields("name"), ErpCustomer.class))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.code()).isEqualTo(BusinessErrorCode.ORDER_CONFLICT);
                    assertThat(business.getMessage()).isEqualTo(BusinessErrorCode.ORDER_CONFLICT.defaultMessage());
                    assertThat(business.getMessage()).doesNotContain("TimestampMismatchError");
                });
    }

    @Test
    @DisplayName("ERPNext 无响应时映射为 ERP_UNAVAILABLE")
    void mapsNoResponseToErpUnavailable() {
        erp.hangOnEveryRequest();

        assertThatThrownBy(() -> client.list(erp.connection("T001"), ErpCustomer.DOCTYPE,
                ErpQuery.create().fields("name"), ErpCustomer.class))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(BusinessErrorCode.ERP_UNAVAILABLE);
    }

    @Test
    @DisplayName("ERPNext 完全不可达时映射为 ERP_UNAVAILABLE")
    void mapsConnectionRefusedToErpUnavailable() {
        // 指向一个没有服务在监听的端口
        ErpConnection unreachable = new ErpConnection("T001", "http://127.0.0.1:1",
                "k", "s", "Standard Selling", null, null, Duration.ofMillis(300), Duration.ofMillis(300));

        assertThatThrownBy(() -> client.list(unreachable, ErpCustomer.DOCTYPE,
                ErpQuery.create().fields("name"), ErpCustomer.class))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(BusinessErrorCode.ERP_UNAVAILABLE);
    }

    @Test
    @DisplayName("ERPNext 原始报文不出现在返回给客户端的错误里")
    void doesNotLeakErpResponseBody() {
        erp.onListStatus(ErpCustomer.DOCTYPE, 500,
                "{\"exception\": \"pymysql.err.ProgrammingError: (1146, \\\"Table 'erp.tabCustomer' doesn't exist\\\")\"}");

        BusinessException exception = catchBusinessException();

        assertThat(exception.getMessage()).isEqualTo(BusinessErrorCode.ERP_UNAVAILABLE.defaultMessage());
        assertThat(exception.getMessage()).doesNotContain("pymysql", "tabCustomer");
        assertThat(exception.details()).isEmpty();
    }

    @Test
    @DisplayName("响应体不是预期结构时不抛原始解析异常")
    void mapsUnparsableBodyToErpUnavailable() {
        erp.onList(ErpCustomer.DOCTYPE, "这不是 JSON");

        assertThatThrownBy(() -> client.list(erp.connection("T001"), ErpCustomer.DOCTYPE,
                ErpQuery.create().fields("name"), ErpCustomer.class))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo(BusinessErrorCode.ERP_UNAVAILABLE);
    }

    @Test
    @DisplayName("data 不是数组时返回空列表而不是抛异常")
    void returnsEmptyListWhenDataIsNotArray() {
        erp.onList(ErpCustomer.DOCTYPE, "{\"data\": null}");

        assertThat(client.list(erp.connection("T001"), ErpCustomer.DOCTYPE,
                ErpQuery.create().fields("name"), ErpCustomer.class)).isEmpty();
    }

    @Test
    @DisplayName("每个请求都带上 Frappe Token 认证头")
    void sendsTokenAuthorizationHeader() throws InterruptedException {
        erp.onList(ErpCustomer.DOCTYPE, "{\"data\": []}");

        client.list(erp.connection("T001"), ErpCustomer.DOCTYPE, ErpQuery.create().fields("name"), ErpCustomer.class);

        assertThat(erp.requests().get(0).getHeader("Authorization")).isEqualTo("token test-key:test-secret");
    }

    private BusinessException catchBusinessException() {
        try {
            client.list(erp.connection("T001"), ErpCustomer.DOCTYPE,
                    ErpQuery.create().fields("name"), ErpCustomer.class);
            throw new AssertionError("期望抛出 BusinessException");
        } catch (BusinessException exception) {
            return exception;
        }
    }
}
