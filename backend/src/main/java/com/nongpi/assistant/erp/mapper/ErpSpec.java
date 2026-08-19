package com.nongpi.assistant.erp.mapper;

import com.nongpi.assistant.erp.dto.ErpItemAttribute;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 规格的唯一推导方式：ERPNext Item Variant Attribute 的属性值。
 *
 * <p>商品选择器和库存页展示的规格必须是同一个值，所以推导逻辑放在这里共用。
 * 非变体商品没有属性行，规格为 null —— 不用 description 这类自由文本充当规格。
 */
public final class ErpSpec {

    private static final String SEPARATOR = " / ";

    private ErpSpec() {
    }

    public static String fromAttributes(List<ErpItemAttribute> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        List<String> values = attributes.stream()
                .sorted(Comparator.comparing(attribute -> attribute.idx() == null ? Integer.MAX_VALUE : attribute.idx()))
                .map(attribute -> ErpValues.trimToNull(attribute.attributeValue()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return values.isEmpty() ? null : String.join(SEPARATOR, values);
    }
}
