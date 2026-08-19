# nongpi-backend

AI 农批经营助手业务后端（Java 21 + Spring Boot 3）。

当前阶段包含 **SaaS 基础设施** 与 **ERPNext 主数据只读链路**。
订单、收款、AI、ASR、Identity 均未开发。

架构约束见仓库根目录 `AGENTS.md` 与 `docs/`。

## 运行

需要 PostgreSQL。集成测试通过 Testcontainers 自动启动，不使用 H2。

```bash
./mvnw test
```

```bash
export APP_JWT_SECRET='至少32字符的签名密钥........'
export APP_CREDENTIAL_ENCRYPTION_KEY='至少一段应用级主密钥'
export DATABASE_URL=jdbc:postgresql://localhost:5432/nongpi
export DATABASE_USERNAME=nongpi
export DATABASE_PASSWORD=nongpi
./mvnw spring-boot:run
```

local profile 引导管理员（没有这些环境变量就不会创建默认账号）：

```bash
export APP_BOOTSTRAP_LOGIN=boss
export APP_BOOTSTRAP_PASSWORD=...
export APP_BOOTSTRAP_TENANT_NAME=农批测试档口
export ERP_BASE_URL=http://localhost:8080
export ERP_SITE_NAME=frontend
export ERP_API_KEY=...
export ERP_API_SECRET=...
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

生产 schema 只由 Flyway 演进，`ddl-auto=validate`。禁止在 migration 里写默认密码或真实 API Secret。

## 配置

一个 SaaS Tenant 对应一个 Frappe / ERPNext Site（`AGENTS.md` #20，已冻结）。
ERP 连接保存在 `erp_connection` 表，API Key / Secret 以 AES-GCM 密文存储。
Master Key 来自 `APP_CREDENTIAL_ENCRYPTION_KEY`，不入库、不进 Git、不写日志。

`selling-price-list` 未配置时不查询商品价格，`referencePrice` 为空 —— 系统不会去猜某个价目表。

不要建设 `ErpInstance` 领域模型，也不要让多个 Tenant 共享同一个 Site。

## 认证

- 登录：`login + password`，BCrypt。Access Token（JWT，约 15 分钟）+ Refresh Token（opaque，约 30 天）
- JWT 含 `userId` / `tenantId` / `membershipId` / `role`，签名密钥来自 `APP_JWT_SECRET`
- Refresh Token 只存 SHA-256，原始值只发给客户端
- 多租户用户登录必须选择 Tenant，否则 `TENANT_SELECTION_REQUIRED`
- `X-Tenant-Id` 一律忽略
- `TenantContextHolder` 仍由现有 Adapter 使用，来源改为已验证的 JWT Membership

公开接口：`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`、`GET /actuator/health`。
其余 `/api/v1/**` 需要认证。OWNER / ADMIN 可改 ERP 连接与 Membership；STAFF 及以上可访问只读经营 API。

## 接口

| 方法   | 路径                             | 说明                                     |
| ------ | -------------------------------- | ---------------------------------------- |
| `POST` | `/api/v1/auth/login`             | 登录                                     |
| `POST` | `/api/v1/auth/refresh`           | 刷新 Access Token                        |
| `POST` | `/api/v1/auth/logout`            | 作废 Refresh Token                       |
| `POST` | `/api/v1/auth/switch-tenant`     | 切换当前企业                             |
| `GET`  | `/api/v1/me`                     | 当前用户与租户                           |
| `GET`  | `/api/v1/memberships`            | 当前企业成员（ADMIN+）                   |
| `POST` | `/api/v1/memberships`            | 新增成员（ADMIN+）                       |
| `PATCH`| `/api/v1/memberships/{id}`       | 更新成员角色/状态（ADMIN+）              |
| `GET`  | `/api/v1/erp-connection`         | 当前企业 ERP 连接（无凭据，ADMIN+）      |
| `PUT`  | `/api/v1/erp-connection`         | 创建/更新 ERP 连接（ADMIN+）             |
| `GET`  | `/api/v1/customers`              | 客户列表 / 搜索，分页                    |
| `GET`  | `/api/v1/customers/{customerId}` | 客户详情                                 |
| `GET`  | `/api/v1/customers/selector`     | 客户选择器                               |
| `GET`  | `/api/v1/products/selector`      | 商品选择器，含 allowedUoms 与参考价格    |
| `GET`  | `/api/v1/inventory`              | 库存查询，支持关键字 / 低库存 / 仓库筛选 |

## 商品身份模型（已冻结）

ERPNext 中 `Item.name == Item.item_code`，不存在独立的 Variant 主键，系统也不制造合成主键。
公开 API 只有两个商品身份字段：

- `itemCode` —— 可交易 ERPNext Item 的**唯一正式身份**。订单行只认它。
- `productId` —— 变体商品取 `Item.variant_of`，非变体商品取自身 `item_code`。
  **只用于商品模板 / 商品族分组**，不是可交易身份。

```text
苹果模板：           productId = APPLE
苹果80果：           productId = APPLE         itemCode = APPLE-80
香蕉粉蕉（非变体）：  productId = BANANA-FEN    itemCode = BANANA-FEN
```

公开 DTO 中已删除 `variantId`，不保留兼容字段。

## ERPNext 映射

| App 字段            | ERPNext 来源                                                     |
| ------------------- | ---------------------------------------------------------------- |
| `customerId`        | `Customer.name`（默认 naming 就是客户名本身，例如「韩兆亮」，不是 CUST-001） |
| `customerName`      | `Customer.customer_name`                                         |
| `phone`             | `Customer.mobile_no`                                             |
| `address`           | `Customer.primary_address`                                       |
| `itemCode`          | `Item.item_code`                                                 |
| `productId`         | `Item.variant_of`，非变体商品回落为自身 `item_code`               |
| `productName`       | `Item.item_name`                                                 |
| `spec`              | `Item Variant Attribute.attribute_value`                         |
| `defaultUom`        | `Item.sales_uom`，缺失时用 `Item.stock_uom`                       |
| `allowedUoms[].uom` | `UOM Conversion Detail.uom`                                      |
| `conversionFactor`  | `UOM Conversion Detail.conversion_factor`                        |
| `referencePrice`    | `Item Price.price_list_rate`（按 UOM 匹配）                       |
| `quantity`          | `Bin.actual_qty`                                                 |
| `stockUom`          | `Bin.stock_uom`                                                  |
| `warehouse`         | `Bin.warehouse`                                                  |
| `alertQty`          | `Item Reorder.warehouse_reorder_level`，退到 `Item.safety_stock`  |
| `aliases`           | SaaS Customer / Product Identity（未开发，返回 `[]`）             |

## referencePrice 用途边界

`referencePrice` 只用于：

- Product Selector 展示
- 订单行的默认参考价

**它不是权威成交价格计算结果。** ERPNext 的实际定价还要结合 Selling Price List、Customer、
UOM、Qty、Currency、Transaction Date、Pricing Rule 等正式业务上下文。

订单模块必须通过 ERPNext 正式定价链路取得成交价，
禁止把 Product Selector 的 `referencePrice` 当作 ERPNext 最终定价结果。

价格与 UOM 绑定：箱价与斤价各自独立，切换单位必须换成该单位的价格。

## 低库存实现说明

ERPNext REST 无法把 `Bin` 与 `Item Reorder` 做联表分页过滤。当前实现：

1. 先查出「配置了补货预警线或安全库存」的候选商品编码 —— 只有这些商品才可能被判定为低库存
2. 用该候选集过滤 `Bin` 查询并分页
3. 映射后按实际数量与预警线比较，剔除有预警配置但当前并不低的行

因此 `lowStock=true` 的分页是在该候选集上进行的。`PageResponse` 不提供 `totalCount`，
也不会给出低库存总数 —— 不制造虚假统计。

这个问题不通过引入 Elasticsearch、本地库存事实表或库存同步机制解决。

## 本阶段无法可靠取得的字段

以下字段刻意不返回，而不是先填一个假值：

- `CustomerSummary.receivableAmount` / `recentOrderTime` —— 需要 ERPNext 财务事实与销售订单历史
- `CustomerSelectorResult.recent` —— 需要最近成交客户，来自 Sales Order 历史
- `ProductSelectorResult.frequentItems` / `lastDealPrice` —— 需要按客户查询成交历史
- `aliases` —— 需要 SaaS Identity 模块
- `lowStock` / `alertQty` —— ERPNext 未配置预警线时为空，表示「无法判断」，不返回任何库存百分比

## Phase 1B 真实 ERPNext 验收

对照官方 `frappe_docker` 标准 ERPNext **v16.32.3** / Frappe **16.31.0** 验证 Adapter。
未改 ERPNext、未建 Custom App。凭据与本地 `application-local.yml` 不进仓库。

已确认：

- `Item.name == Item.item_code`；`has_variants=1` 的模板不会进入选择器
- 变体 `productId = variant_of`（APPLE-80 → APPLE），非变体 `productId = itemCode`（BANANA-FEN）
- `spec` 取 `Item Variant Attribute.attribute_value` 原文（实测为「80果」，不是另外编一个「80mm」）
- 子表批量查询必须带 `parent=Item`；DocType 名带空格时路径必须编码为 `%20`，不能是 `+`
- Item Price 按 UOM 分开；没有价格行就不返回 `referencePrice`，不伪造
- `Bin.actual_qty` 与 Reorder / `safety_stock` 预警可用；未配置时 `safety_stock` 会返回 `0.0`，不把它当预警线
- `Customer.primary_address` 只有设置了 `customer_primary_address` 才会填充，值是 HTML，Mapper 压成单行纯文本；未设置则字段缺失，不伪造地址
- 中文 LIKE、手机号搜索、`limit_start` 分页可用；`filters` 与 `or_filters` 是 AND

标准 REST 本阶段够用，不便之处记为限制，不为此建 Custom App：

- 无法把 Bin 与 Item Reorder 做联表分页（低库存仍用候选集过滤，见上一节）
- 列表接口不提供可靠 `totalCount`
- Product Selector 没有独立 ERP API，当前最多返回 30 条
- 搜索是字段 LIKE，不是全文检索

## 技术决策记录

| 决策                                          | 状态                                    |
| --------------------------------------------- | --------------------------------------- |
| Phase 1 临时 Token → Tenant Filter            | 已替换为 Spring Security + JWT          |
| PostgreSQL + Flyway                           | Phase 2 已落地                          |
| Spring Security / User / Membership           | Phase 2 已落地                          |
| 数据库 `ErpConnectionProvider`                | Phase 2 已落地，Adapter 接口不变        |
| ERP 凭据 AES-GCM 加密                         | Phase 2 已落地，Master Key 来自环境变量 |
| 一个 SaaS Tenant 对应一个 Frappe/ERPNext Site | 已冻结；禁止多 Tenant 共享 Site         |

补充说明：

- **部署模型已冻结**：一个 SaaS Tenant 对应一个 Frappe/ERPNext Site。`ErpConnectionProvider`
  按租户从数据库解密该 Site 的连接。不要建设 `ErpInstance`，也不要把 Site URL 写进业务 Service。
