# 04_DOMAIN_MODEL.md

AI 农批经营助手领域模型与 ERPNext 映射设计。

版本：2.3

## 1. 文档目的

本文档定义 AI 农批经营助手中的业务概念，与 ERPNext 业务对象之间的映射关系。

目标是确保三件事始终一致：

- App 展示的业务对象
- AI 理解和生成的业务动作
- ERPNext 保存的正式业务数据

核心原则：

> ERPNext 负责保存企业事实，AI 负责理解老板表达，App 负责让老板快速完成业务。

------

## 2. 系统数据分层

系统中的数据分为两类。

### 2.1 ERPNext 业务事实

ERPNext 是业务事实唯一来源。

包括：

- Customer：客户
- Item：商品
- Sales Order：销售订单
- Sales Order Item：订单商品明细
- Payment Entry：收款
- Stock / Bin / Warehouse：库存
- Accounting：财务

这些数据代表企业真实发生的业务。

AI 系统不得建立第二套同类主业务数据。

### 2.2 AI 增强数据

AI 系统只保存帮助理解业务的数据。

例如：

- 客户常用称呼
- 商品简称
- ASR 常见误识别
- 企业语言习惯
- 短期上下文
- AI 操作日志
- 用户纠错结果

例如：

老韩 → ERPNext Customer「韩兆亮」

八零 → ERPNext Item「苹果80果」

这些映射用于帮助 AI 理解老板，不替代 ERPNext 主数据。

------

## 3. Customer 客户

### ERPNext 对象

Customer

### ERPNext 负责保存

- 客户正式名称
- 联系方式
- 地址
- 联系人
- 客户分组
- 信用相关信息
- 历史业务关联

例如：

- Customer ID：CUST-001
- Customer Name：韩兆亮

### AI 增强：Customer Identity

现实中老板不一定使用正式姓名。

例如 ERPNext 中客户为：

韩兆亮

老板可能说：

- 老韩
- 亮哥
- 韩老板
- 韩照亮（ASR 错误结果）

因此 AI 侧维护 Customer Identity。

建议结构：

- id
- tenant_id
- erp_customer_id
- expression
- type
- source
- status
- confidence
- created_at
- updated_at

type 可包括：

- alias：常用称呼
- nickname：昵称
- voice_variant：语音识别变体
- historical_expression：历史表达

status：

- candidate
- confirmed
- rejected
- deprecated

注意：

Customer Identity 只是「表达 → ERPNext Customer」映射，不创建新的 Customer。

------

## 4. Item 商品

### ERPNext 对象

Item

### ERPNext 负责保存

- Item Code
- 商品名称
- 规格
- 默认单位
- 商品组
- 价格相关信息
- 库存关联

例如：

- Item Code：APPLE-80
- Item Name：苹果80果
- UOM：箱

### 商品身份语义（已冻结）

ERPNext 中：

```
Item.name == Item.item_code
```

不存在需要我们自行制造的独立 Variant 主键。

因此商品身份只有两个字段：

`itemCode`

= 可交易 ERPNext Item 的唯一正式身份

= 订单行只认它

`productId`

= Variant 时取 `Item.variant_of`

= 非 Variant 时取自身 `itemCode`

= 只用于商品模板 / 商品族分组，不是可交易身份

例如：

```
苹果模板：
productId = APPLE

苹果80果：
productId = APPLE
itemCode  = APPLE-80

香蕉粉蕉（非变体）：
productId = BANANA-FEN
itemCode  = BANANA-FEN
```

禁止制造 `P001V80` 这种 ERPNext 不存在的合成主键。

### 商品价格

商品参考价格来自 ERPNext Item Price，按 UOM 绑定。

参考价格只用于商品选择展示与订单行默认值。

它不是最终成交价：ERPNext 实际定价还要结合 Selling Price List、Customer、
UOM、Qty、Currency、Transaction Date、Pricing Rule 等正式业务上下文。

订单模块必须通过 ERPNext 正式定价链路取得成交价。

### AI 增强：Product Identity

老板可能不说 ERPNext 中的完整商品名称。

例如：

ERPNext：

苹果80果

老板：

八零

AI 维护：

八零 → APPLE-80

建议结构：

- id
- tenant_id
- erp_item_code
- expression
- type
- source
- status
- confidence

商品别名同样不能替代 ERPNext Item。

------

## 5. Sales Order 销售订单

### ERPNext 对象

Sales Order

Sales Order 是订单主对象。

一张订单对应一个客户，但可以包含多个商品明细。

核心结构：

- customer
- transaction_date
- delivery_date（如果业务需要）
- items[]
- total / grand_total
- order status
- payment information
- remarks

必须明确：

> 一张订单不是一个商品。

正确关系是：

Customer
→ Sales Order
→ 多个 Sales Order Item

例如一张订单：

客户：韩兆亮

商品明细：

1. 苹果80果，20箱，68元/箱
2. 香蕉粉蕉，30件，32元/件
3. 阳光玫瑰，10箱，120元/箱

这些商品属于同一张 Sales Order。

### 5.1 公开 ID 与 ERPNext 主键（Phase 3 已冻结）

公开 API 直接使用 ERPNext 主键，不另造一层业务 ID：

- `orderId` = `Sales Order.name`
- `orderItemId` = `Sales Order Item.name`（创建时客户端不传）
- `paymentId` = `Payment Entry.name`

禁止在公开 DTO 中再返回 `erpSalesOrderId` / `erpPaymentEntryId`。

`POST /api/v1/orders` 永远只创建 Draft（`docstatus=0`）。Submit 只走 `POST /api/v1/orders/{orderId}/submit`。普通 `PUT` 仅允许 Draft；已提交订单只读。

`confirmedPaid` 优先取 `Sales Order.advance_paid`。经营待收金额字段名为 `remainingToCollect`，不是会计科目上的 `outstanding`。Draft Payment Entry 不计。付清只改 `paymentStatus=PAID`，不把 `orderStatus` 改为 COMPLETED。

付款方式来自 ERPNext Mode of Payment。当前 Company 未配置默认账户时返回 `PAYMENT_METHOD_NOT_CONFIGURED`，不猜测会计科目。

Phase 3 真实 ERPNext v16 Probe：标准字段 `remarks` 创建后不会落库，因此本阶段不持久化订单 `note`。`delivery_date` 若必填，默认等于 `transaction_date`，不表示配送流程。

------

## 6. Sales Order Item 订单商品明细

### ERPNext 对象

Sales Order Item

每个商品是一条明细。

建议 UI 和 AI 至少使用以下字段：

- item_code
- item_name
- description / specification
- qty
- uom
- rate
- amount

例如：

商品 1：

- Item：APPLE-80
- 名称：苹果80果
- 数量：20
- 单位：箱
- 单价：68
- 金额：1360

商品 2：

- Item：BANANA-01
- 名称：香蕉粉蕉
- 数量：30
- 单位：件
- 单价：32
- 金额：960

订单总金额由商品明细计算，不在 AI 中自行维护另一套计算规则。

------

## 7. AI 创建订单

AI 不直接创造订单事实。

例如老板说：

> 老韩要20箱八零，再加30件粉蕉，还是以前价格，下午来拿。

AI 首先生成结构化理解结果：

- customer_reference：老韩
- items：
  - product_reference：八零
  - quantity：20
  - unit：箱
  - product_reference：粉蕉
  - quantity：30
  - unit：件
- price_reference：以前价格
- remark：下午来拿

随后进行：

1. Customer Identity Resolver
2. Product Identity Resolver
3. 查询 ERPNext 客户
4. 查询 ERPNext 商品
5. 查询历史价格
6. 生成订单草稿
7. 打开统一订单编辑页
8. 用户可修改任意字段
9. 提交到 ERPNext Sales Order

AI 创建订单与手动创建订单最终必须进入同一个 Sales Order 模型。

------

## 8. Order Draft 订单草稿（V1 边界已冻结）

Draft 分两层，必须区分清楚。

### 8.1 客户端编辑状态

新增 / 编辑订单过程中，只是 Flutter 本地编辑状态，可以存在 App 或短期 Context 中。

这种草稿不是企业事实，**不创建 ERPNext Sales Order**。

AI 解析出的订单同样只是普通订单编辑页的 Draft State。

AI 解析完成不自动创建 ERPNext Sales Order。

这一层允许字段暂未完成。

### 8.2 ERPNext Draft Sales Order

用户点击「保存草稿」时：

数据通过最低正式订单校验后，才创建 ERPNext `docstatus=0` Draft Sales Order。

ERPNext Draft 创建之后：

后续「保存修改」更新同一张 Sales Order，禁止创建新单。

用户点击「提交订单」：

Submit 同一张 ERPNext Sales Order，之后由 ERPNext 管理后续状态。

### 8.3 V1 明确规则

V1 不引入 PostgreSQL Order Working Draft 表。

V1 不支持把存在无效商品行的订单持久化为正式 ERP Draft。

例如商品已选但数量为空：保存草稿时提示填写，不静默删除该商品行，
也不建立第二套订单草稿事实。

原则：

> 不建立独立的 AI Order 数据库。

需要持久化业务草稿时，只使用 ERPNext Draft。

注意：早期「保存草稿允许任意不完整商品行」不再成立。
「允许不完整」只适用于 8.1 的客户端编辑状态。

------

## 9. 订单状态与付款状态必须分离

之前原型将「待收款」作为订单状态，这是错误的。

### 订单状态

订单状态描述订单本身。

例如：

- Draft：草稿
- To Deliver and Bill / Submitted：已提交
- Completed：已完成
- Cancelled：已取消

不要伪造订单「待确认」。App 可以对 ERPNext 状态做更适合老板理解的展示映射，但不能创造冲突的业务状态。

### 付款状态

付款状态描述资金情况。

例如：

- 未收款
- 部分收款
- 已收款

付款状态应该根据 ERPNext 中的正式财务/单据事实计算或映射。

不要将付款状态混入 Sales Order 生命周期。

------

## 10. Payment Entry 收款

### ERPNext 对象

Payment Entry

收款属于独立业务事实。

例如老板说：

> 王老板微信收了5000。

AI理解：

- customer_reference：王老板
- amount：5000
- payment_method：微信
- direction：收款

之后：

1. 解析客户
2. 查询 ERPNext 往来关系
3. 生成 Payment Entry 草稿
4. 进入收款编辑页面
5. 用户检查或补充
6. Spring Boot 做权限和业务校验
7. ERPNext 保存 Payment Entry

AI 不单独保存正式收款数据。

------

## 11. 欠款 / 应收

App 可以展示：

- 应收金额
- 已收金额
- 未收金额

但这些金额必须来源于 ERPNext 财务事实。

不要自己维护一个独立的「欠款字段」作为事实来源。

客户详情页可以把这些数据组织成老板容易理解的形式，例如：

- 当前应收：¥8,600
- 最近收款：¥5,000
- 最近收款日期：8月18日

但底层仍以 ERPNext 为准。

------

## 12. Stock 库存

库存事实属于 ERPNext。

相关对象可能包括：

- Item
- Warehouse
- Bin
- Stock Ledger
- Stock Entry

AI 只负责查询或生成业务操作意图。

例如老板说：

> 八零还有多少？

流程：

八零
→ Product Identity
→ APPLE-80
→ ERPNext 库存查询
→ 返回库存结果

AI 数据库不得保存一份独立的实时库存余额作为事实源。

------

## 13. Task / Reminder 提醒事项

老板可能说：

> 11点半提醒我给王老板备货。

这类信息不一定属于 ERPNext 核心经营单据。

V1 可以由 SaaS 平台维护 Reminder / Task 数据。

用途：

- 时间提醒
- 与客户关联
- 与订单关联
- 完成状态

它属于 SaaS 增强能力，而不是 ERP 主业务事实。

未来如果需要，可通过 Adapter 映射 ERPNext ToDo / Task。

------

## 14. AI Action

AI 不直接执行数据库操作。

AI 的正式输出应是 Business Action。

例如：

create_sales_order_draft

结构示例：

- intent
- customer_reference
- resolved_customer_id
- items[]
- remarks
- missing_fields[]
- ambiguity[]
- confidence / evidence
- target_business_page

其中 items 必须是数组。

禁止再使用：

product + quantity

这种只能表达单商品订单的结构。

------

## 15. 多商品 AI Action 示例

老板说：

> 老韩苹果20箱，香蕉30件，葡萄再来10箱。

AI Action 应表达为：

- action：create_sales_order_draft
- customer：韩兆亮
- items：
  - 苹果80果 / 20箱
  - 香蕉粉蕉 / 30件
  - 葡萄 / 10箱

如果某一个商品存在歧义，只处理该商品的歧义。

例如：

- 苹果80果：已解析
- 香蕉粉蕉：已解析
- 葡萄：需要选择规格

不要因为一个商品不确定，让老板重新说整个订单。

------

## 16. App 订单列表映射

订单列表展示 Sales Order 的摘要，而不是一个商品。

如果订单只有一个商品：

韩兆亮
苹果80果 ×20箱

如果有多个商品：

韩兆亮
苹果80果 ×20箱 等3种商品

或者：

韩兆亮
苹果80果 ×20箱、香蕉 ×30件 +1

同时展示：

- 总金额
- 订单状态
- 付款状态
- 时间

------

## 17. App 订单编辑页映射

订单编辑页必须采用：

### 订单头

- 客户
- 日期
- 备注

### 商品明细

每一行商品：

- 商品
- 规格
- 数量
- 单位
- 单价
- 小计

支持：

- 修改商品
- 修改数量
- 修改价格
- 删除商品
- 添加商品

提供：

「+ 添加商品」

### 金额汇总

展示：

- 商品种类数量
- 商品总件数/箱数可按业务需要展示
- 合计金额

### 底部操作

根据状态显示：

- 保存草稿
- 提交订单

AI 与手工录入必须共用这个页面。

------

## 18. App 订单详情页映射

订单详情页不能再以「单商品详情」为结构。

正确结构：

### 客户与订单状态

### 商品明细列表

例如：

苹果80果
20箱 × ¥68
¥1,360

香蕉粉蕉
30件 × ¥32
¥960

### 金额汇总

订单总额：¥2,320

### 付款情况

已收：¥1,000
未收：¥1,320

### 备注

下午自提

### 操作记录

- 创建草稿
- 修改商品
- 修改价格
- 提交订单
- 收款记录

------

## 19. Customer 页面映射

客户详情应以「交易关系」为重点，而不是 CRM 画像。

展示建议：

- 客户名称
- 电话
- 地址
- 常用称呼
- 当前应收
- 最近订单
- 常买商品
- 最近成交价格
- 历史订单
- 最近收款

其中：

客户正式资料和交易事实来自 ERPNext。

常用称呼来自 AI Customer Identity。

------

## 20. Product 页面映射

商品详情可以组合：

ERPNext：

- Item
- 规格
- 单位
- 当前价格
- 库存

AI：

- 商品简称
- 老板常用叫法
- ASR 常见变体

例如：

苹果80果

- Item Code：APPLE-80
- 单位：箱
- 库存：450箱
- 老板常叫：八零

------

## 21. 数据归属表

| 数据            | 唯一事实来源             |
| --------------- | ------------------------ |
| 客户主体        | ERPNext Customer         |
| 客户电话/地址   | ERPNext                  |
| 客户常用称呼    | SaaS AI Identity         |
| 商品主体        | ERPNext Item             |
| 商品规格        | ERPNext Item             |
| 商品简称        | SaaS AI Identity         |
| 订单            | ERPNext Sales Order      |
| 订单商品        | ERPNext Sales Order Item |
| 收款            | ERPNext Payment Entry    |
| 应收/欠款       | ERPNext 财务事实         |
| 库存            | ERPNext Stock            |
| 短期 AI Context | Redis / AI Service       |
| AI 企业知识     | PostgreSQL               |
| 提醒事项        | SaaS Platform            |
| AI 操作日志     | PostgreSQL               |

------

## 22. 禁止事项

禁止创建第二套：

- Customer 主数据
- Item 主数据
- Sales Order 事实
- Payment 事实
- Inventory 事实

禁止：

LLM → ERPNext Database

禁止：

AI 直接提交高风险业务操作而绕过 Spring Boot 权限和业务校验。

禁止：

用单一 product / qty 字段描述 Sales Order。

------

## 23. 最终数据关系

Customer
↓
Sales Order
↓
Sales Order Item[]

Payment Entry 与 Customer / 相关业务单据建立财务关联。

Item
↓
Stock

AI 增强层：

Customer Identity
→ ERPNext Customer

Product Identity
→ ERPNext Item

AI Action
→ Spring Boot
→ ERPNext Adapter
→ ERPNext

------

## 24. 最终原则

> ERPNext 管理客户、商品、订单、收款和库存等正式企业事实。

> AI 管理老板语言与 ERPNext 业务对象之间的理解关系。

> 一张订单天然支持多个商品，AI 和手动创建必须共用同一套订单模型和订单编辑页面。
