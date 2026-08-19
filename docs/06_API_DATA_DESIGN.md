# 06_API_DATA_DESIGN.md

版本：3.0
状态：Development Baseline

# AI 农批经营助手 API 与数据设计

## 1. 文档目的

本文档定义 AI 农批经营助手的：

- Flutter 对外 API
- Spring Boot 业务 API
- Python AI Service 内部接口
- ERPNext Adapter 边界
- SaaS / AI 数据归属
- 核心业务 DTO
- AI Action 数据结构
- 多商品订单数据结构
- Product / Variant / UOM 数据结构
- Payment 数据结构
- 多租户与错误模型

目标：

> App、AI、Spring Boot 与 ERPNext 使用同一套业务语义，不建立第二套 ERP 事实。

------

# 2. 总体调用链路

正式生产链路：

Flutter App

→ Nginx

→ Spring Boot Business Platform

内部再分为：

Spring Boot
→ ERPNext Adapter
→ ERPNext

以及：

Spring Boot
→ Python FastAPI AI Service
→ Model Gateway / ASR / AI Knowledge

禁止：

Flutter → ERPNext

禁止：

Flutter → Python AI Service

禁止：

Flutter → LLM Provider

禁止：

Python AI Service → ERPNext Database

------

# 3. API 设计原则

## 3.1 Spring Boot 是唯一公开业务 API

Flutter 只调用 Spring Boot。

Spring Boot 负责：

- Authentication
- Tenant Resolution
- Permission
- Business Validation
- ERPNext Adapter
- AI Service 调用
- Audit
- Error Mapping

------

## 3.2 ERPNext 是正式业务事实来源

正式业务事实包括：

- Customer
- Item / Variant
- Sales Order
- Sales Order Item
- Payment Entry
- Stock
- Warehouse
- Accounting

PostgreSQL 不复制这些数据成为第二事实源。

------

## 3.3 AI 输出业务动作，不直接写业务事实

AI Service 输出：

Business Action

例如：

- create_order
- update_current_order
- record_payment
- query_customer
- query_inventory

Spring Boot 决定：

- 是否允许
- 是否补充数据
- 是否查询 ERPNext
- 是否创建 Draft
- 是否需要用户进一步选择

------

# 4. API Base

示例：

```
/api/v1
```

所有公开接口均通过版本前缀。

例如：

```
GET /api/v1/orders
POST /api/v1/orders
POST /api/v1/ai/actions
```

具体 URL 可在开发阶段按现有项目规范调整，但领域结构不可随意改变。

------

# 5. Tenant 规则

Tenant 不直接信任 Flutter 自己提交的业务 tenantId。

Tenant 应通过：

- 登录 Session
- Access Token
- Server-side User Context
- Tenant Membership

解析。

后端所有核心操作必须明确：

```
TenantContext
```

禁止：

客户端传一个 tenantId 就直接访问该租户数据。

------

# 6. 核心引用对象

前端 DTO 中区分：

ID

和：

显示名称。

例如客户：

```json
{
  "customerId": "C001",
  "customerName": "韩兆亮"
}
```

商品：

```json
{
  "productId": "APPLE",
  "itemCode": "APPLE-80",
  "productName": "苹果80果",
  "spec": "80mm"
}
```

ERPNext 中 `Item.name == Item.item_code`，不存在独立的 Variant 主键。

因此商品身份只有两个字段：

`itemCode`

= 可交易 ERPNext Item 的唯一正式身份

`productId`

= Variant 时取 `Item.variant_of`

= 非 Variant 时取自身 `itemCode`

= 只用于商品模板 / 商品族分组

非变体商品示例：

```json
{
  "productId": "BANANA-FEN",
  "itemCode": "BANANA-FEN",
  "productName": "香蕉粉蕉"
}
```

禁止制造 `P001V80` 这种 ERPNext 不存在的合成主键。

正式业务不能只使用：

```json
{
  "customer": "韩兆亮",
  "product": "苹果80果"
}
```

作为唯一身份。

------

# 7. Customer DTO

## CustomerSummary

建议：

```text
customerId
customerName
aliases[]
phone
receivableAmount
recentOrderTime
```

例如：

```json
{
  "customerId": "C001",
  "customerName": "韩兆亮",
  "aliases": ["老韩", "亮哥"],
  "phone": "138****3456",
  "receivableAmount": 1360,
  "recentOrderTime": "2026-08-19T10:20:00+08:00"
}
```

------

# 8. Customer 查询

建议接口：

```
GET /api/v1/customers
```

参数：

```text
q
page
pageSize
```

`q` 可以匹配：

- Customer 正式名称
- 电话
- Customer Identity Alias

例如：

```
q=老韩
```

后端：

Alias Resolver
→ ERP Customer ID
→ Customer Summary

------

# 9. Customer Selector API

移动端选择器可以使用：

```
GET /api/v1/customers/selector
```

可返回：

```text
recent[]
results[]
```

例如：

```json
{
  "recent": [
    {
      "customerId": "C001",
      "customerName": "韩兆亮",
      "aliases": ["老韩", "亮哥"]
    }
  ],
  "results": []
}
```

最近交易客户不应在 results 中重复。

------

# 10. Product 数据层次

UI 领域层次：

Product

→ Variant / ERP Item

→ Allowed UOM

正式交易对象最终必须解析到：

ERPNext Item / Item Code。

Product 可以作为 App 聚合概念。

例如：

苹果

包含：

- 苹果70果
- 苹果75果
- 苹果80果
- 苹果85果

真正进入 Sales Order Item 的是具体 Variant / Item。

------

# 11. Product DTO

建议：

```text
productId
itemCode
productName
spec
aliases[]
defaultUom
allowedUoms[]
referencePrice
priceUom
currency
stock
stockUom
lowStock
```

例如：

```json
{
  "productId": "APPLE",
  "itemCode": "APPLE-80",
  "productName": "苹果80果",
  "spec": "80mm",
  "aliases": ["八零", "八零苹果"],
  "defaultUom": "箱",
  "allowedUoms": [
    {
      "uom": "箱",
      "referencePrice": 68,
      "conversionFactor": 1
    },
    {
      "uom": "斤",
      "referencePrice": 3.8,
      "conversionFactor": 20
    }
  ],
  "referencePrice": 68,
  "priceUom": "箱",
  "currency": "CNY",
  "stock": 450,
  "stockUom": "箱",
  "lowStock": false
}
```

`conversionFactor` 只有 ERPNext / Adapter 能可靠提供时才返回。

App 不建立第二套换算事实。

`referencePrice` 只用于展示与默认参考价，不是成交价，边界见第 16 节。

`lowStock` 只有在 ERPNext 存在明确预警配置时才有值，否则不返回。

------

# 12. UOM 原则

每个 Item / Variant 返回自己的：

```
allowedUoms[]
```

禁止 Flutter 自己维护：

箱 / 斤 / 件 / 盒

这样的全局静态列表作为业务规则。

只有一个 UOM：

前端只读。

多个合法 UOM：

前端允许选择。

------

# 13. Product Selector API

建议：

```
GET /api/v1/products/selector
```

参数：

```text
q
customerId
```

`q` 匹配：

- Item Name
- Product Alias
- Variant Name
- Spec
- Product Identity Alias

如果带：

```
customerId
```

则可以同时返回：

```
frequentItems[]
```

和：

```
results[]
```

------

# 14. Product Selector 返回

例如：

```json
{
  "frequentItems": [
    {
      "productId": "APPLE",
      "itemCode": "APPLE-80",
      "productName": "苹果80果",
      "spec": "80mm",
      "defaultUom": "箱",
      "referencePrice": 68,
      "lastDealPrice": 65,
      "lastDealUom": "箱",
      "stock": 450
    }
  ],
  "results": []
}
```

------

# 15. 历史成交价

历史成交价查询条件至少包括：

```text
tenant
customerId
itemCode
uom
```

不能只按商品名称。

例如：

韩兆亮 + APPLE-80 + 箱

可以得到：

¥65 / 箱

如果当前 UOM 切换成：

斤

必须重新查：

韩兆亮 + APPLE-80 + 斤

如果没有历史成交：

返回 null。

------

# 16. 历史成交价 API

可以由 Product Selector 直接聚合返回。

也可以提供：

```
GET /api/v1/pricing/last-deal
```

参数：

```text
customerId
itemCode
uom
```

返回：

```json
{
  "price": 65,
  "uom": "箱",
  "sourceOrderId": "SO-000123",
  "transactionTime": "2026-08-18T10:20:00+08:00"
}
```

MVP 优先聚合到商品选择接口，减少 App 请求次数。

**referencePrice 与成交价的区别**

`referencePrice` 来自 ERPNext Item Price，只用于：

- Product Selector 展示
- 订单行的默认参考价

它不是最终权威成交价格计算器。

ERPNext 的实际定价还需要结合正式业务上下文：

- Selling Price List
- Customer
- UOM
- Qty
- Currency
- Transaction Date
- Pricing Rule

订单模块必须通过 ERPNext 正式定价链路取得成交价。

禁止把 Product Selector 返回的 `referencePrice`
直接当作 ERPNext 最终定价结果。

`lastDealPrice` 是历史事实，同样不是当前定价结果。

------

# 17. Sales Order DTO

一张订单支持多个商品。

正式 DTO：

```text
orderId
erpSalesOrderId
customerId
customerName
transactionDate
items[]
orderStatus
paymentStatus
totalAmount
paidAmount
outstandingAmount
note
createdAt
updatedAt
```

------

# 18. Sales Order Item DTO

建议：

```text
orderItemId
productId
itemCode
productName
spec
qty
uom
rate
amount
lastDealPrice
```

例如：

```json
{
  "orderItemId": "ITEM-1",
  "productId": "APPLE",
  "itemCode": "APPLE-80",
  "productName": "苹果80果",
  "spec": "80mm",
  "qty": 20,
  "uom": "箱",
  "rate": 68,
  "amount": 1360,
  "lastDealPrice": 65
}
```

------

# 19. 禁止单商品订单 API

禁止设计：

```json
{
  "customerId": "C001",
  "itemCode": "APPLE-80",
  "qty": 20,
  "price": 68
}
```

订单必须使用：

```json
{
  "items": []
}
```

------

# 20. 创建订单草稿

前提：编辑过程中不调用本接口。

新增 / 编辑订单过程中只是客户端本地编辑状态，
不产生 ERPNext Sales Order（详见第 24 节）。

只有用户点击「保存草稿」时才调用：

```
POST /api/v1/orders
```

请求：

```json
{
  "mode": "draft",
  "customerId": "C001",
  "items": [
    {
      "itemCode": "APPLE-80",
      "qty": 20,
      "uom": "箱",
      "rate": 68
    },
    {
      "itemCode": "BANANA-FEN",
      "qty": 30,
      "uom": "件",
      "rate": 32
    }
  ],
  "note": "下午来拿"
}
```

------

# 21. 提交订单

可以：

```
POST /api/v1/orders/{orderId}/submit
```

或者创建时：

```
mode=submit
```

具体实现按 ERPNext Adapter 能力决定。

业务上必须明确：

Draft

和：

Submitted

不能通过模糊的 `save=true` 表达。

------

# 22. 更新订单

建议：

```
PUT /api/v1/orders/{orderId}
```

或：

```
PATCH /api/v1/orders/{orderId}
```

请求仍然使用：

```
items[]
```

ERPNext Draft 已经存在之后，「保存修改」更新同一张 Sales Order。

禁止更新订单时把其重新创建为新 Order。

------

# 23. 订单提交校验

Spring Boot 最终校验：

- Customer 存在
- Customer 属于当前 Tenant
- Item 存在
- Item 可交易
- UOM 合法
- Qty > 0
- Rate >= 0
- 至少一个 Item
- 当前 Order 状态允许操作
- 用户权限允许操作

Flutter 校验只用于 UX。

不能替代后端校验。

------

# 24. Draft 边界（V1 已冻结）

Draft 分两层，必须区分清楚。

**第一层：客户端编辑状态**

新增 / 编辑订单过程中，只是 Flutter 本地编辑状态。

不立即创建 ERPNext Sales Order。

AI 解析出的订单同样只是普通订单编辑页的 Draft State。

AI 解析完成不自动创建 ERPNext Sales Order。

这一层允许字段暂未完成。

**第二层：ERPNext Draft Sales Order**

用户点击「保存草稿」时：

数据通过最低正式订单校验后，
才创建 ERPNext `docstatus=0` Draft Sales Order。

ERPNext Draft 创建之后：

后续「保存修改」更新同一张 Sales Order。

禁止创建新单。

用户点击「提交订单」：

Submit 同一张 ERPNext Sales Order。

**V1 明确规则**

V1 不引入 PostgreSQL Order Working Draft 表。

V1 不支持把存在无效商品行的订单持久化为正式 ERP Draft。

例如商品已选但数量为空：

保存草稿时提示填写。

不静默删除该商品行（参见第 45 节禁止静默删除）。

也不建立第二套订单草稿事实。

**已废弃的说法**

早期「保存草稿允许任意不完整商品行」不再成立。

「允许不完整」只适用于第一层客户端编辑状态，
不适用于创建 ERPNext Draft Sales Order。

------

# 25. Order Status

App 展示状态必须通过 Adapter / Business Mapping 得出。

V1 展示：

```text
草稿
待确认
已提交
已完成
已取消
```

不要让 Flutter 自己根据几个字段推导 ERPNext 状态。

------

# 26. Payment Status

付款状态：

```text
未收款
部分收款
已收款
```

计算依据：

正式已到账 Payment / 财务事实。

不要作为 Sales Order 状态的一部分。

------

# 27. Order List API

建议：

```
GET /api/v1/orders
```

参数：

```text
q
status
page
pageSize
from
to
```

`q` 可以匹配：

- Customer
- Item
- Order ID

------

# 28. Order Summary Response

例如：

```json
{
  "orderId": "ORD001",
  "customerId": "C001",
  "customerName": "韩兆亮",
  "itemSummary": "苹果80果20箱 等2种商品",
  "itemCount": 2,
  "totalAmount": 2320,
  "orderStatus": "DRAFT",
  "orderStatusLabel": "草稿",
  "paymentStatus": "UNPAID",
  "paymentStatusLabel": "未收款",
  "transactionTime": "2026-08-19T10:20:00+08:00"
}
```

------

# 29. Order Detail API

```
GET /api/v1/orders/{orderId}
```

返回：

完整 Order + Items + Payment Summary + History。

例如：

```text
totalAmount
paidAmount
outstandingAmount
```

应由服务端计算。

------

# 30. Payment 正式模型

正式 Payment API 不能只保存客户姓名。

至少：

```text
paymentId
customerId
customerName
amount
paymentMethod
status
relatedOrderId
erpPaymentEntryId
transactionTime
note
```

------

# 31. Payment Draft

例如：

```json
{
  "customerId": "C001",
  "customerName": "韩兆亮",
  "amount": 1000,
  "paymentMethod": "WECHAT",
  "status": "CONFIRMED",
  "relatedOrderId": "ORD001",
  "note": ""
}
```

------

# 32. Payment Method

App 可展示：

```text
微信转账
现金
银行转账
```

API 不建议直接把中文 UI 文案作为唯一业务值。

建议：

```text
WECHAT_TRANSFER
CASH
BANK_TRANSFER
```

同时返回：

```
paymentMethodLabel
```

------

# 33. Payment Confirmation Status

建议内部：

```text
CONFIRMED
PENDING_CONFIRMATION
```

UI：

```text
已到账
待确认
```

------

# 34. 创建 Payment

建议：

```
POST /api/v1/payments
```

正式写入前：

Spring Boot 校验：

- Customer
- Amount
- Method
- Related Order
- Permission
- Tenant

再通过：

ERPNext Payment Adapter

创建对应正式财务对象。

------

# 35. 收款确认

如果存在：

待确认 Payment

建议：

```
POST /api/v1/payments/{paymentId}/confirm
```

确认后必须重新查询/计算：

- confirmedPaid
- outstanding
- paymentStatus

禁止只改 Payment 自己的状态。

------

# 36. 付款汇总计算

定义：

```text
confirmedPaid =
当前订单相关所有正式已到账收款合计
outstanding =
max(orderTotal - confirmedPaid, 0)
```

规则：

```text
confirmedPaid <= 0
→ UNPAID
0 < confirmedPaid < total
→ PARTIAL
confirmedPaid >= total
→ PAID
```

------

# 37. Order 与 Payment Status 解耦

Payment 全额完成：

不自动：

```
orderStatus = COMPLETED
```

订单完成条件由正式订单业务生命周期决定。

例如：

付款完成但货尚未提走：

Payment = PAID

Order 仍可能不是 Completed。

------

# 38. 补收尾款 API

Flutter 不应该自己计算最终可信 outstanding。

打开补收尾款页面时，可以调用：

```
GET /api/v1/orders/{orderId}/payment-summary
```

返回：

```json
{
  "totalAmount": 2320,
  "confirmedPaid": 1000,
  "outstandingAmount": 1320,
  "paymentStatus": "PARTIAL"
}
```

App 预填：

1320。

------

# 39. Stock Query

建议：

```
GET /api/v1/inventory
```

参数：

```text
q
lowStock
warehouseId
page
pageSize
```

返回按 Item / Variant 维度的数据。

------

# 40. Stock Response

例如：

```json
{
  "itemCode": "APPLE-80",
  "productId": "APPLE",
  "productName": "苹果80果",
  "spec": "80mm",
  "quantity": 450,
  "stockUom": "箱",
  "warehouse": "主仓库 - T",
  "lowStock": false,
  "alertQty": 50
}
```

Stock 事实来自 ERPNext。

`lowStock` 与 `alertQty` 只有在 ERPNext 存在明确预警配置
（`Item Reorder.warehouse_reorder_level` 或 `Item.safety_stock`）时才返回。

没有预警配置时不返回这两个字段，表示「无法判断」，不能默认成 false。

------

# 41. 禁止虚构库存百分比

除非存在明确：

- Capacity
- Target Stock
- Max Stock

否则 API 不返回：

```text
stockPercent
```

App 也不自己计算：

stock / 500。

------

# 42. AI 公开入口

建议：

```
POST /api/v1/ai/actions
```

可以统一处理：

- text
- voice transcription
- page context

------

# 43. AI Action Request

示例：

```json
{
  "inputType": "TEXT",
  "text": "苹果改成30箱",
  "context": {
    "currentPage": "ORDER_EDIT",
    "currentOrderId": "ORD001",
    "currentCustomerId": "C001",
    "currentCustomerName": "韩兆亮",
    "currentItems": [
      {
        "itemCode": "APPLE-80",
        "productName": "苹果80果",
        "qty": 20,
        "uom": "箱"
      },
      {
        "itemCode": "BANANA-FEN",
        "productName": "香蕉粉蕉",
        "qty": 30,
        "uom": "件"
      }
    ]
  }
}
```

这是正式开发必须补齐的内容。

原型中的 `currentItems` 不能继续为空。

------

# 44. AI Context 原则

允许传给 AI Service 的上下文应该是：

完成当前用户意图所需的最小业务上下文。

包括：

- currentPage
- currentObject
- currentCustomer
- currentOrder
- currentItems

不要把整个 ERP 数据库、整个用户历史都塞进 Prompt。

------

# 45. AI Action Response

建议统一：

```text
actionId
actionType
status
targetPage
resolvedEntities
ambiguities
payload
```

------

# 46. create_order

例如：

```json
{
  "actionType": "CREATE_ORDER",
  "targetPage": "ORDER_EDIT",
  "payload": {
    "customer": {
      "customerId": "C001",
      "customerName": "韩兆亮"
    },
    "items": [
      {
        "itemCode": "APPLE-80",
        "productId": "APPLE",
        "productName": "苹果80果",
        "spec": "80mm",
        "qty": 20,
        "uom": "箱",
        "rate": 65
      }
    ]
  }
}
```

------

# 47. update_current_order

例如：

输入：

苹果改30箱

返回：

```json
{
  "actionType": "UPDATE_CURRENT_ORDER",
  "targetPage": "ORDER_EDIT",
  "payload": {
    "orderId": "ORD001",
    "operations": [
      {
        "operation": "SET_QTY",
        "itemCode": "APPLE-80",
        "qty": 30,
        "uom": "箱"
      }
    ]
  }
}
```

AI 不直接写 Order。

Flutter / Spring Boot 根据 Draft Context 应用动作。

正式持久化仍走正常订单 API。

------

# 48. AI 追加商品

例如：

再加10箱葡萄。

返回：

```json
{
  "operation": "ADD_ITEM",
  "itemCode": "GRAPE-01",
  "qty": 10,
  "uom": "箱"
}
```

如果 Product Resolver 无法唯一确认具体 Variant：

不得生成假 itemCode。

返回 ambiguity。

------

# 49. AI Ambiguity

例如：

苹果20箱。

存在多个规格：

```text
APPLE-70
APPLE-75
APPLE-80
APPLE-85
```

返回：

```json
{
  "status": "NEED_USER_INPUT",
  "ambiguities": [
    {
      "field": "item",
      "expression": "苹果",
      "candidates": [
        {
          "itemCode": "APPLE-70",
          "name": "苹果70果",
          "spec": "70mm"
        },
        {
          "itemCode": "APPLE-80",
          "name": "苹果80果",
          "spec": "80mm"
        }
      ]
    }
  ]
}
```

保留已确定：

- Customer
- Qty
- UOM
- 其他 Items

------

# 50. AI Customer Resolution

Python AI Service 负责：

表达理解与候选排序。

Spring Boot / Resolver 数据层负责取得 Tenant 内候选。

例如：

```text
老韩
韩照亮
韩老板
```

→ Candidate Customer IDs

AI 不得跨 Tenant 查询别名。

------

# 51. AI Product Resolution

同样：

```text
八零
80果
苹果80
```

→ ERP Item / Variant Candidates

最终 Action 必须引用：

```
itemCode
```

不能只留下：

`"苹果80果"`。

------

# 52. ASR 接口

推荐将 ASR 抽象为独立内部能力。

例如内部：

```
POST /internal/ai/speech/transcribe
```

输入：

Audio Object Reference

输出：

```json
{
  "text": "老韩苹果80果20箱",
  "segments": [],
  "provider": "..."
}
```

业务系统不依赖特定 ASR Provider。

------

# 53. Audio Storage

语音文件：

优先使用 S3-compatible Object Storage。

不要永久 Base64 存 PostgreSQL。

保存周期由隐私策略决定。

AI Debug 需要录音时：

必须有明确生命周期。

------

# 54. Python AI Service 内部接口

外部用户不直接访问。

建议：

```
POST /internal/ai/parse-action
```

Spring Boot 提供：

- Tenant-scoped context
- Allowed Candidates
- User Input
- Current Business Context

Python 返回：

结构化 Action。

------

# 55. Model Gateway

Python 内部统一通过：

Model Gateway

调用模型。

业务代码禁止绑定：

Qwen-only
OpenAI-only
DeepSeek-only

------

# 56. PostgreSQL SaaS 数据

可以保存：

- Tenant
- User
- Membership
- ERP Connection Config
- Customer Identity
- Product Identity
- AI Action Log
- AI Feedback
- AI Knowledge
- Reminder

------

# 57. PostgreSQL 禁止成为第二业务事实源

不要创建长期事实表：

- `orders`
- `order_items`
- `payments`
- `inventory_balance`

来与 ERPNext 双写。

如果为：

Cache / Projection / Search Index

临时存在：

必须明确标记：

非 System of Record。

------

# 58. Customer Identity

建议：

```text
id
tenantId
erpCustomerId
expression
expressionType
source
status
confidence
usageCount
lastConfirmedAt
```

------

# 59. Product Identity

建议：

```text
id
tenantId
erpItemCode
expression
expressionType
source
status
confidence
usageCount
lastConfirmedAt
```

------

# 60. Identity 隔离

唯一约束必须包含：

Tenant。

例如：

```text
tenantId + expression
```

不能出现：

A 商户的“老韩”

自动映射到 B 商户的韩兆亮。

------

# 61. Redis

用于：

- Session
- Short-term AI Context
- Draft Conversation Context
- Cache
- Rate Limit

不保存正式订单事实。

------

# 62. AI Draft Context

Redis 可以短期保存：

```text
conversationId
currentPage
currentOrderId
currentCustomerId
currentItems
expiresAt
```

用于：

“刚才那个改成30箱。”

这种短期指代。

------

# 63. Audit

Spring Boot 重要写操作必须产生 Audit。

建议：

```text
tenantId
userId
action
targetType
targetId
aiActionId
timestamp
result
```

------

# 64. AI Action Log

建议保存：

- rawInput
- asrText
- intent
- extractedEntities
- Resolver Candidates
- selectedEntities
- correction
- finalAction
- finalBusinessObjectRef
- latency
- modelProvider
- modelName

但不保存正式业务余额作为 AI 事实。

------

# 65. 统一错误模型

建议：

```json
{
  "code": "ITEM_AMBIGUOUS",
  "message": "商品存在多个可能结果",
  "traceId": "...",
  "details": {}
}
```

------

# 66. 核心错误 Code

至少：

```text
CUSTOMER_NOT_FOUND
CUSTOMER_AMBIGUOUS
ITEM_NOT_FOUND
ITEM_AMBIGUOUS
INVALID_UOM
INVALID_QUANTITY
INVALID_RATE
ORDER_NOT_FOUND
ORDER_INVALID
ORDER_STATUS_INVALID
PAYMENT_INVALID
PAYMENT_EXCEEDS_POLICY
PERMISSION_DENIED
TENANT_NOT_FOUND
ERP_UNAVAILABLE
AI_UNAVAILABLE
ASR_UNAVAILABLE
```

------

# 67. AI 不可用

AI Service 故障：

手动 API 必须继续工作。

例如：

```
POST /orders
```

不能依赖 Python AI Service 在线。

------

# 68. ERPNext 不可用

如果 ERPNext 正式保存失败：

API 不得返回：

Success。

例如：

```text
ERP_UNAVAILABLE
```

Flutter 可以保留当前尚未提交的本地编辑状态并支持重试。

------

# 69. ERPNext Adapter

Spring Boot Adapter 负责：

App Domain

↔

ERPNext DTO

映射。

禁止：

Controller 直接拼 ERPNext 请求。

------

# 70. Customer Mapping

App：

```text
customerId
customerName
```

Adapter：

→ ERPNext Customer

具体 ERPNext 字段名封装在 Adapter 内。

------

# 71. Item Mapping

App：

```text
itemCode
qty
uom
rate
```

Adapter：

→ ERPNext Sales Order Item

Flutter 不需要知道 ERPNext 内部字段细节。

------

# 72. Sales Order Mapping

App Order：

→ ERPNext Sales Order

App OrderItem[]：

→ ERPNext Sales Order Item[]

订单总额以 ERPNext 业务计算结果为正式结果。

App 本地金额计算只是即时 UX。

------

# 73. Payment Mapping

App Payment：

→ ERPNext Payment Entry

具体：

- Account
- Mode of Payment
- Party
- Reference Document

由 Adapter / Business Service 处理。

不要让 Flutter 直接处理会计字段。

------

# 74. Stock Mapping

App Inventory：

→ ERPNext Stock / Bin / Warehouse / Stock Ledger 等查询结果。

具体底层对象由 Adapter 屏蔽。

------

# 75. API 幂等性

重要创建操作应考虑 Idempotency。

尤其：

- Create Order
- Submit Order
- Create Payment

可使用：

```
Idempotency-Key
```

防止网络重试造成重复订单 / 重复收款。

------

# 76. 乐观并发

订单可能被多终端修改。

更新时建议使用：

```text
version
updatedAt
```

之一进行并发检测。

冲突时：

```
ORDER_CONFLICT
```

不要静默覆盖。

------

# 77. 金额精度

正式后端金额禁止使用二进制浮点作为最终财务运算类型。

Java：

使用：

```
BigDecimal
```

数据库：

Decimal / Numeric

Flutter 显示层可以使用适合其平台的 Decimal 策略。

------

# 78. 数量精度

农批数量可能不是整数。

例如：

```text
12.5斤
3.25公斤
```

Qty API 不应强制 integer。

使用 Decimal。

------

# 79. 时间

API 返回：

ISO 8601

带时区。

例如：

```
2026-08-19T10:20:00+08:00
```

不要返回：

“今天 10:20”

这种 UI 文案作为业务事实。

“今天 / 昨天”由 Flutter 格式化。

------

# 80. 分页

订单、客户、商品、收款、库存列表均考虑分页。

建议：

```text
page
pageSize
```

或 Cursor pagination。

MVP 可先使用传统分页。

------

# 81. Search

App Search 的 API 不能一次拉全量数据后本地过滤。

生产环境：

搜索应后端执行。

尤其：

- Customer
- Item
- Orders
- Inventory

------

# 82. 安全

所有业务 API：

Authentication

→ Tenant Resolution

→ Authorization

→ Validation

→ Business Service

→ Adapter

禁止绕过。

------

# 83. 敏感配置

ERPNext Credentials：

不返回 Flutter。

Model Provider API Key：

不返回 Flutter。

ASR Provider Key：

不返回 Flutter。

全部服务端管理。

------

# 84. V1 核心 API 集

开发第一阶段至少需要：

```text
GET    /customers
GET    /customers/{id}
GET    /customers/selector

GET    /products/selector
GET    /inventory

GET    /orders
GET    /orders/{id}
POST   /orders
PUT    /orders/{id}
POST   /orders/{id}/submit

GET    /payments
POST   /payments
POST   /payments/{id}/confirm
GET    /orders/{id}/payment-summary

POST   /ai/actions
```

------

# 85. 第一条手工黄金路径

Flutter：

选择客户

→ Customer Selector API

添加商品

→ Product Selector API

选择 UOM / Rate

→ POST Order Draft

→ Submit Order

→ ERPNext

------

# 86. 第一条 AI 黄金路径

用户：

> 老韩80果20箱，粉蕉30件。

Flutter：

Audio / Text

→ Spring Boot AI API

→ Python Intent + Resolver

→ customerId + itemCodes

→ 返回 Order Draft Payload

→ Flutter 打开普通 Order Edit

→ 用户修改

→ POST /orders

→ ERPNext

------

# 87. AI 修改黄金路径

当前：

ORD001

用户：

> 苹果改30箱。

Context：

包含 ORD001 + Items。

AI：

返回 UPDATE_CURRENT_ORDER。

Flutter：

修改当前 Draft State。

用户：

提交修改。

→ PUT Order

→ ERPNext

------

# 88. Payment 黄金路径

订单：

¥2,320

第一次收：

¥1,000

→ Payment Entry

→ Payment Status = PARTIAL

第二次：

¥1,320

→ Payment Entry

→ confirmedPaid = 2,320

→ Payment Status = PAID

Order Status 不自动变化。

------

# 89. 最终数据原则

> ERPNext 保存企业正式发生的业务事实。

> Spring Boot 管理确定性业务规则、安全、权限和 ERPNext 操作。

> Python AI Service 负责理解用户表达和产生结构化 Business Action。

> Flutter 负责高效输入和展示。

> Customer、Item、Variant、UOM、Order、Payment 在所有层必须保持统一身份引用。

------

# 90. 最终禁止事项

禁止：

单商品 Order API。

禁止：

正式 Order 只保存 Customer Name。

禁止：

Order Item 只保存 Product Name。

禁止：

自由输入不存在的 UOM 并提交。

禁止：

AI 猜测无法唯一解析的 Item。

禁止：

Payment 只根据本次金额更新付款状态。

禁止：

全额收款自动完成订单。

禁止：

Flutter 直接访问 ERPNext。

禁止：

Python AI Service 直接修改 ERPNext 正式业务数据。
