package com.nongpi.assistant.product.service;

import com.nongpi.assistant.common.error.BusinessErrorCode;
import com.nongpi.assistant.common.error.BusinessException;
import com.nongpi.assistant.erp.adapter.ItemErpAdapter;
import com.nongpi.assistant.erp.adapter.SalesOrderErpAdapter;
import com.nongpi.assistant.erp.connection.ErpConnection;
import com.nongpi.assistant.erp.connection.ErpConnectionProvider;
import com.nongpi.assistant.product.domain.AllowedUom;
import com.nongpi.assistant.product.domain.ProductVariant;
import com.nongpi.assistant.tenant.TenantContext;
import com.nongpi.assistant.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("商品单位校验")
class ProductServiceUomTest {

    private static final ErpConnection CONNECTION = new ErpConnection(
            "T001", "http://erp.test", "k", "s", "Standard Selling", null, "农批测试", null, null);

    private ProductService productService;
    private Map<String, ProductVariant> catalog;

    @BeforeEach
    void setUp() {
        catalog = Map.of(
                // 变体商品：productId 是模板 APPLE，itemCode 是可交易身份 APPLE-80
                "APPLE-80", new ProductVariant("APPLE", "APPLE-80", "苹果80果", "80mm",
                        List.of(), "箱",
                        List.of(new AllowedUom("箱", BigDecimal.ONE, new BigDecimal("68"), "CNY"),
                                new AllowedUom("斤", new BigDecimal("20"), new BigDecimal("3.8"), "CNY")),
                        new BigDecimal("68"), "箱", "CNY", null),
                // 非变体商品：productId 回落为自身 itemCode
                "BANANA-FEN", new ProductVariant("BANANA-FEN", "BANANA-FEN", "香蕉粉蕉", null,
                        List.of(), "件",
                        List.of(new AllowedUom("件", BigDecimal.ONE, new BigDecimal("32"), "CNY")),
                        new BigDecimal("32"), "件", "CNY", null));

        ItemErpAdapter adapter = new ItemErpAdapter() {
            @Override
            public List<ProductVariant> search(ErpConnection connection, String keyword, int offset, int limit) {
                return List.copyOf(catalog.values());
            }

            @Override
            public Optional<ProductVariant> findByItemCode(ErpConnection connection, String itemCode) {
                return Optional.ofNullable(catalog.get(itemCode));
            }
        };
        ErpConnectionProvider connectionProvider = tenant -> CONNECTION;

        productService = new ProductService(adapter, mock(SalesOrderErpAdapter.class), connectionProvider, (tenant, itemCodes) -> Map.of());
        TenantContextHolder.set(new TenantContext("T001", "徐州水果档口"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("合法单位返回与该单位绑定的参考价")
    void acceptsAllowedUom() {
        AllowedUom jin = productService.requireAllowedUom("APPLE-80", "斤");

        assertThat(jin.uom()).isEqualTo("斤");
        // 价格必须跟着单位走，不能返回箱价
        assertThat(jin.referencePrice()).isEqualByComparingTo("3.8");
        assertThat(jin.conversionFactor()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("该商品不支持的单位返回 INVALID_UOM，并带上合法单位列表")
    void rejectsUomNotConfiguredOnItem() {
        assertThatThrownBy(() -> productService.requireAllowedUom("APPLE-80", "袋"))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> {
                    BusinessException exception = (BusinessException) thrown;
                    assertThat(exception.code()).isEqualTo(BusinessErrorCode.INVALID_UOM);
                    assertThat(exception.details()).containsEntry("uom", "袋");
                    assertThat(exception.details()).containsEntry("allowedUoms", List.of("箱", "斤"));
                });
    }

    @Test
    @DisplayName("单单位商品只接受它自己的那一个单位")
    void singleUomItemRejectsOtherUoms() {
        assertThat(productService.requireAllowedUom("BANANA-FEN", "件").uom()).isEqualTo("件");

        assertThatThrownBy(() -> productService.requireAllowedUom("BANANA-FEN", "箱"))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).code())
                .isEqualTo(BusinessErrorCode.INVALID_UOM);
    }

    @Test
    @DisplayName("未指定单位返回 INVALID_UOM")
    void rejectsBlankUom() {
        assertThatThrownBy(() -> productService.requireAllowedUom("APPLE-80", "  "))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).code())
                .isEqualTo(BusinessErrorCode.INVALID_UOM);
    }

    @Test
    @DisplayName("商品不存在时返回 ITEM_NOT_FOUND")
    void rejectsUnknownItem() {
        assertThatThrownBy(() -> productService.requireAllowedUom("GHOST-1", "箱"))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).code())
                .isEqualTo(BusinessErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("缺少租户上下文时拒绝查询，不回落到默认租户")
    void requiresTenantContext() {
        TenantContextHolder.clear();

        assertThatThrownBy(() -> productService.selector("苹果", null))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).code())
                .isEqualTo(BusinessErrorCode.PERMISSION_DENIED);
    }
}
