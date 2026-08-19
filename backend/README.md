# nongpi-backend

AI 农批经营助手业务后端（Java 21 + Spring Boot 3）。

当前阶段只包含 **ERPNext 主数据只读链路**：客户、商品 / 变体、合法单位、参考价格、库存基础查询。
订单、收款、AI、ASR 均未开发。

架构约束见仓库根目录 `AGENTS.md` 与 `docs/`。

## 运行

```bash
./mvnw spring-boot:run
```

```bash
./mvnw test
```

测试全部使用本地假 ERPNext（MockWebServer），不需要真实 ERPNext 或外网。

## 配置

租户与 ERPNext 连接来自服务端配置。默认 `app.tenants` 为空，此时所有业务接口返回
`PERMISSION_DENIED`，不存在「默认租户」这种回落。

```yaml
app:
  tenants:
    - tenant-id: T001
      tenant-name: 徐州水果档口
      access-tokens:
        - ${TENANT_T001_TOKEN}
      erp:
        base-url: https://erp.example.com
        api-key: ${TENANT_T001_ERP_API_KEY}
        api-secret: ${TENANT_T001_ERP_API_SECRET}
        selling-price-list: Standard Selling
        default-warehouse: 主仓库 - T
        connect-timeout: 3s
        read-timeout: 10s
```

`selling-price-list` 未配置时不查询商品价格，`referencePrice` 为空 —— 系统不会去猜某个价目表。

ERPNext 多租户部署方式尚未冻结（`AGENTS.md` #20）。不同租户配置不同 `base-url` 就是每租户独立部署，
配置相同 `base-url` 就是共享部署，代码两种都支持，不做架构决策。

## 认证（临时实现）

`TenantContextFilter` 从 `Authorization: Bearer <token>` 解析租户，Token 只存在于服务端配置。

请求头里的 `X-Tenant-Id` 一律忽略：客户端不能自己声明租户身份。

正式用户体系（User / Membership / 权限）属于下一阶段，届时替换 `TenantResolver` 实现，
下游 `TenantContextHolder` 用法不变。

## 接口

| 方法  | 路径                             | 说明                                     |
| ----- | -------------------------------- | ---------------------------------------- |
| `GET` | `/api/v1/customers`              | 客户列表 / 搜索，分页                    |
| `GET` | `/api/v1/customers/{customerId}` | 客户详情                                 |
| `GET` | `/api/v1/customers/selector`     | 客户选择器                               |
| `GET` | `/api/v1/products/selector`      | 商品选择器，含 allowedUoms 与参考价格    |
| `GET` | `/api/v1/inventory`              | 库存查询，支持关键字 / 低库存 / 仓库筛选 |

## ERPNext 映射

| App 字段              | ERPNext 来源                                        |
| --------------------- | --------------------------------------------------- |
| `customerId`          | `Customer.name`                                     |
| `customerName`        | `Customer.customer_name`                            |
| `phone`               | `Customer.mobile_no`                                |
| `address`             | `Customer.primary_address`                          |
| `itemCode`            | `Item.item_code`                                    |
| `variantId`           | `Item.name`（ERPNext 中与 `item_code` 同值）        |
| `productId`           | `Item.variant_of`，非变体商品回落为自身 `item_code`  |
| `productName`         | `Item.item_name`                                    |
| `spec`                | `Item Variant Attribute.attribute_value`            |
| `defaultUom`          | `Item.sales_uom`，缺失时用 `Item.stock_uom`          |
| `allowedUoms[].uom`   | `UOM Conversion Detail.uom`                         |
| `conversionFactor`    | `UOM Conversion Detail.conversion_factor`           |
| `referencePrice`      | `Item Price.price_list_rate`（按 UOM 匹配）          |
| `quantity`            | `Bin.actual_qty`                                    |
| `stockUom`            | `Bin.stock_uom`                                     |
| `warehouse`           | `Bin.warehouse`                                     |
| `alertQty`            | `Item Reorder.warehouse_reorder_level`，退到 `Item.safety_stock` |
| `aliases`             | SaaS Customer / Product Identity（未开发，返回 `[]`） |

## 本阶段无法可靠取得的字段

以下字段刻意不返回，而不是先填一个假值：

- `CustomerSummary.receivableAmount` / `recentOrderTime` —— 需要 ERPNext 财务事实与销售订单历史
- `CustomerSelectorResult.recent` —— 需要最近成交客户，来自 Sales Order 历史
- `ProductSelectorResult.frequentItems` / `lastDealPrice` —— 需要按客户查询成交历史
- `aliases` —— 需要 SaaS Identity 模块
- `lowStock` / `alertQty` —— ERPNext 未配置预警线时为空，表示「无法判断」，不返回任何库存百分比
