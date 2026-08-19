# AGENTS.md

# AI 农批经营助手 — 开发 Agent 总规则

状态：Development Baseline
适用对象：Cursor、Codex、Claude Code 及其他 Coding Agent

本文档是本项目开发阶段的核心约束。

任何 Agent 在进行较大功能开发、架构修改、领域模型调整或跨模块修改之前，必须先阅读本文件，并按照本文档规定的产品、架构、数据和交互边界执行。

本项目不是实验性 AI Demo。

目标是建设一套可以长期商业化运营的：

**农批经营 SaaS + AI 智能操作层**

------

# 1. 产品定位

本产品面向：

- 农产品批发市场老板
- 档口经营者
- 中小批发商户

产品本质是：

> 一套成熟的移动经营软件，同时让老板可以直接通过自然语言操作业务。

ERPNext 提供成熟业务基础能力。

本项目在 ERPNext 之上建设：

- 移动端经营体验
- SaaS 用户体系
- 多租户体系
- AI 自然语言操作能力
- 企业个性化语言理解
- 农批场景业务体验

核心原则：

> 没有 AI，软件依然必须完整可用。

AI 是一种操作方式，不是产品本身。

------

# 2. 禁止把产品做成什么

禁止将产品做成：

- AI 聊天机器人
- AI 语音录单 Demo
- ERPNext 手机换皮
- ERP + AI 对话框
- 单纯 CRM
- 单纯进销存
- AI Agent 展示项目
- PC ERP 缩小到手机

正确方向：

> 高频、清晰、可靠、老板每天真正会使用的移动经营工具。

AI 的价值：

**少点、少输、少找。**

------

# 3. 没有 AI 也必须完整可用

必须存在传统操作入口：

- 手动创建订单
- 手动修改订单
- 查询订单
- 选择客户
- 管理客户
- 选择商品
- 管理商品
- 查询库存
- 记录收款
- 查看收款记录

禁止为了体现 AI Native：

删除传统操作入口。

------

# 4. 当前项目按新项目建设

不要默认迁移、兼容或复用旧 AI Order Clerk 项目设计。

除非明确要求，否则：

- 不迁移旧 Runtime
- 不复制旧领域模型
- 不复制旧确认机制
- 不因为旧代码改变新架构
- 不为了旧实现牺牲新产品设计

旧项目只能作为经验参考。

------

# 5. 开发前文档阅读顺序

较大功能开始之前，按以下顺序阅读：

1. `AGENTS.md`
2. `docs/01_PRODUCT_VISION.md`
3. `docs/02_ARCHITECTURE_DECISION.md`
4. `docs/03_PRODUCT_FLOW.md`
5. `docs/04_DOMAIN_MODEL.md`
6. `docs/05_UI_SPEC.md`
7. `docs/06_API_DATA_DESIGN.md`
8. `docs/07_TECH_STACK_DECISION.md`
9. `docs/08_AI_ENGINE_DESIGN.md`
10. `docs/09_DEVELOPMENT_PLAN.md`

如果存在：

```
docs/README.md
```

只作为导航索引。

不要继续无意义增加大量文档。

------

# 6. 文档冲突优先级

发生冲突时：

1. 当前明确的产品决策
2. `01_PRODUCT_VISION.md`
3. `02_ARCHITECTURE_DECISION.md`
4. `04_DOMAIN_MODEL.md`
5. `06_API_DATA_DESIGN.md`
6. `03_PRODUCT_FLOW.md`
7. `05_UI_SPEC.md`
8. `08_AI_ENGINE_DESIGN.md`
9. `09_DEVELOPMENT_PLAN.md`

发现冲突：

先指出。

禁止 Agent 自行创造第三套方案。

------

# 7. Agent 职责

Agent 负责：

- 按确定设计实现代码
- 完成功能
- 修复 Bug
- 编写测试
- 保持架构一致
- 发现问题并报告
- 做最小必要修改

Agent 不负责自行重新定义：

- 产品定位
- 总体架构
- 技术栈
- ERPNext 数据边界
- 核心领域模型
- AI 产品形态
- 多租户架构策略

如果发现设计问题：

先报告原因和影响。

不要擅自重构整个项目。

------

# 8. 总体生产架构

生产逻辑架构：

Flutter App

↓

Nginx

↓

Spring Boot Business Platform

↓

两条主要路径：

Spring Boot
→ ERPNext Adapter
→ ERPNext

以及：

Spring Boot
→ Python FastAPI AI Service
→ Model Gateway / ASR / Knowledge

------

# 9. Flutter 职责

Flutter 负责：

- 页面展示
- 用户输入
- 表单状态
- 页面导航
- 录音
- UI 校验
- Draft 编辑体验
- 错误提示

Flutter 不负责：

- ERPNext 业务规则
- Tenant 权限
- 客户最终身份判断
- 商品最终身份判断
- 正式订单状态规则
- 正式付款计算
- 财务规则

禁止 Flutter 直接访问：

- ERPNext
- PostgreSQL
- Redis
- Python AI Service 内部接口
- LLM Provider

所有正式业务请求必须进入 Spring Boot。

------

# 10. Nginx

Nginx 负责：

- HTTPS / TLS
- Reverse Proxy
- API 入口
- 基础限流
- 静态资源
- 网络配置

禁止在 Nginx 写业务逻辑。

------

# 11. Spring Boot

固定：

- Java 21
- Spring Boot 3.x

Spring Boot 是业务控制中心。

负责：

- Authentication
- User
- Tenant
- Membership
- Authorization
- 公开业务 API
- 确定性业务流程
- ERPNext Adapter
- AI Service 调度
- Audit
- 风险控制
- Business Validation

核心原则：

> Python 负责理解，Spring Boot 负责决定是否允许执行。

所有正式写操作必须经过 Spring Boot。

------

# 12. Python AI Service

技术：

FastAPI

负责：

- Model Gateway
- Prompt Management
- Intent Recognition
- Entity Extraction
- Customer Identity Resolver
- Product Identity Resolver
- ASR Integration
- Context Understanding
- Knowledge Retrieval
- AI Evaluation
- Business Action Generation

Python 不拥有企业正式业务事实。

禁止：

Python → ERPNext Database

禁止：

Python 绕过 Spring Boot 直接提交正式业务单据。

------

# 13. ERPNext

ERPNext 是：

**System of Record**

正式保存：

- Customer
- Item
- Item Variant
- UOM
- Sales Order
- Sales Order Item
- Payment Entry
- Stock
- Warehouse
- Accounting
- 其他正式 ERP 对象

禁止自建第二套 ERP。

------

# 14. PostgreSQL 数据边界

PostgreSQL 用于 SaaS / AI 增强数据。

允许：

- Tenant
- User
- Membership
- ERP Connection Metadata
- Customer Identity
- Product Identity
- Knowledge
- AI Action Log
- AI Feedback
- Reminder
- Evaluation Data

禁止作为正式事实中心保存：

- Sales Order
- Sales Order Item
- Payment Entry
- Stock Balance
- Accounting Balance

------

# 15. Redis

用于：

- Session
- Cache
- Rate Limit
- Short-term AI Context
- 临时 Draft Context

Redis 不是业务事实数据库。

禁止只在 Redis 保存正式订单或正式付款。

------

# 16. RabbitMQ

仅真正需要异步任务时使用。

例如：

- AI Evaluation
- 异步通知
- 文件处理
- 后台事件

MVP 不为“架构完整”强行加入异步复杂度。

------

# 17. Model Gateway

LLM 必须通过统一 Model Gateway。

业务代码不得绑定单一 Provider。

目标兼容：

- OpenAI
- Qwen
- DeepSeek
- OpenAI-compatible Provider
- 未来其他模型

Provider 差异必须收敛在 Gateway。

------

# 18. ERPNext Adapter

所有 ERPNext 调用统一通过 Adapter。

禁止：

- Flutter 直接访问 ERPNext
- Controller 到处拼 ERPNext HTTP
- Python 直接操作 ERPNext
- 业务 Service 到处出现 ERPNext 特有字段

Adapter 负责：

- API 封装
- DTO Mapping
- Error Mapping
- ERPNext 字段差异
- ERPNext 版本隔离

------

# 19. Tenant 隔离

所有 SaaS / AI 数据必须带 Tenant 边界。

禁止通过客户端传入的 tenantId 直接信任租户身份。

Tenant 通过：

- Access Token
- Session
- User Membership
- Server Context

解析。

必须避免：

- 跨租户客户别名
- 跨租户商品别名
- 跨租户 Context
- 跨租户 Knowledge
- 跨租户业务查询

------

# 20. ERPNext 多租户部署模型

已冻结：

> 一个 SaaS Tenant 对应一个 Frappe / ERPNext Site。

未来 `Tenant` 与 `ErpConnection` / `ErpSiteConnection` 是 1:1。

禁止：

- 多 Tenant 共享同一个 Site
- 建设复杂 `ErpInstance` 领域模型来表达「一台 ERPNext 跑多个 Tenant」

当前开发只使用一个 Site。连接仍通过 `ErpConnectionProvider` 按租户解析，不要把 Site URL 写死在业务代码里。

------

# 21. Customer 身份规则

正式订单必须引用：

- customerId
- customerName

不能只保存：

```
"韩兆亮"
```

客户正式身份来自 ERPNext Customer。

------

# 22. Customer Identity

AI 允许理解：

正式名称：

韩兆亮

老板表达：

- 老韩
- 亮哥
- 韩老板

ASR 变体：

- 韩照亮
- 韩兆良
- 韩少亮

AI 侧 Customer Identity 负责：

表达 → ERP Customer

但不创建第二个 Customer。

------

# 23. Customer Resolver

综合：

- Exact Name
- Confirmed Alias
- Nickname
- ASR Variant
- 拼音/发音相似
- 历史纠错
- 最近业务上下文
- Tenant Knowledge

唯一高可信候选：

可以预填。

多个合理候选：

只处理这一处歧义。

------

# 24. 局部歧义原则

例如：

老板说：

老韩苹果80果20箱。

客户有：

韩兆亮
韩兆良

商品和数量已经确定。

系统只问：

> 是韩兆亮还是韩兆良？

禁止：

- 清空订单
- 清空商品
- 要用户重说整句话

------

# 25. Product 不是单一字符串

商品模型必须支持：

Product

→ Variant / Item

→ Allowed UOM

例如：

苹果

→ 苹果70果 / 70mm
→ 苹果75果 / 75mm
→ 苹果80果 / 80mm
→ 苹果85果 / 85mm

真正进入 Sales Order Item 的必须是明确 ERP Item / Variant。

------

# 26. Product / Item 身份语义（已冻结）

ERPNext 中：

```
Item.name == Item.item_code
```

不存在需要我们自行制造的独立 Variant 主键。

正式身份统一为两个字段：

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

禁止制造：

```
P001V80
```

这种 ERPNext 不存在的合成主键。

正式 OrderItem 至少必须拥有：

- orderItemId
- productId
- itemCode
- productName
- spec
- qty
- uom
- rate
- amount

禁止只保存：

```
productName + spec
```

作为商品身份。

------

# 27. Product Identity

允许企业内部简称：

八零

→ 苹果80果

粉蕉

→ 香蕉粉蕉

产品身份知识必须 Tenant 隔离。

------

# 28. Product Resolver

Resolver 综合：

- Item Name
- Variant
- Alias
- Spec
- Voice Variant
- Tenant 历史知识
- 当前业务上下文

最终输出：

```
itemCode
```

不能只输出自然语言商品名。

------

# 29. 规格规则

规格不允许在正式订单中随意自由填写。

如果：

70果
75果
80果
85果

对应不同 ERP Item / Variant：

必须选择具体 Variant。

选完后规格只读。

如果存在其他正式 ERP Variant 模型：

Adapter 映射处理。

------

# 30. UOM 规则

每个 Variant 拥有：

- allowedUoms[]
- defaultUom

例如：

苹果80果：

- 箱
- 斤

香蕉：

- 件
- 箱

禁止 Flutter 写死一个全局单位列表。

禁止用户任意输入不存在的 UOM。

------

# 31. UOM 交互

一个合法 UOM：

只读。

多个：

允许用户从 Allowed UOM 中选择。

选择 UOM 后：

- 更新 UOM
- 更新对应参考价格
- 重新查询历史成交价
- 重新计算 UI 小计

------

# 32. UOM 与价格

价格必须与 UOM 绑定。

例如：

苹果80果：

箱 → ¥68/箱
斤 → ¥3.8/斤

禁止：

从箱切到斤后仍然保留 ¥68。

**referencePrice 用途边界**

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

禁止把 Product Selector 的 `referencePrice`
直接当作 ERPNext 最终定价结果。

------

# 33. 历史成交价

至少按：

Tenant

- Customer
- Variant / Item Code
- UOM

查询。

例如：

韩兆亮
APPLE-80
箱

→ ¥65 / 箱

不能把：

¥65/箱

应用到：

斤。

------

# 34. Sales Order 核心规则

Sales Order 天然是：

订单主表

- 

```
items[]
```

禁止设计：

product
qty
price

这种单商品 Order。

------

# 35. 一张订单多个商品

例如：

老韩：

苹果80果 20箱
香蕉粉蕉 30件
阳光玫瑰 10箱

这是：

**一张 Sales Order**

不是三张。

除非用户明确要求拆单。

------

# 36. Order 正式身份

`orderId` 等于 ERPNext `Sales Order.name`。公开 API 不再返回 `erpSalesOrderId`。

订单至少包含：

- orderId
- customerId
- customerName
- items[]
- orderStatus
- paymentStatus
- totalAmount
- confirmedPaid
- remainingToCollect
- timestamps

`updatedAt` 等于 ERPNext `modified`，用于乐观锁。

Phase 3 实测标准 `remarks` 创建后不会落库，因此本阶段不持久化 `note`。

------

# 37. OrderItem 正式身份

`orderItemId` 等于 ERPNext `Sales Order Item.name`（创建时客户端不传，由 ERPNext 生成）。

OrderItem 至少：

- orderItemId
- productId
- itemCode
- productName
- spec
- qty
- uom
- rate
- amount

金额正式结果以 ERPNext 为准。

App 本地计算只是 UX。

------

# 38. 金额类型

Java 正式财务计算使用：

```
BigDecimal
```

禁止：

float / double

作为最终财务计算依据。

------

# 39. 数量类型

数量可能是：

12.5斤
3.25公斤

不要把 Qty 强制设计为 Integer。

使用 Decimal-compatible 类型。

------

# 40. 新增订单 UI

移动端禁止 PC 表格：

商品｜数量｜单价｜小计

订单商品使用：

1～2 行交易列表。

例如：

苹果80果 · 80mm　　　　　　　¥1,360 ＞
20箱 × ¥68/箱　　　　上次 ¥65/箱

整行可点。

不显示铅笔图标。

------

# 41. 商品编辑 UI

点击商品：

打开 Bottom Sheet。

可以编辑：

- Qty
- 合法 UOM
- Rate

商品名称、规格：

只读。

需要更换商品：

重新进入 Product Selector。

------

# 42. Customer Selector

订单客户不能普通手输后直接提交。

必须绑定正式 Customer。

Selector 支持：

- Name
- Alias
- Phone
- Recent Customers

------

# 43. Product Selector

支持：

- Product Name
- Alias
- Variant Name
- Spec

如果带 Customer：

优先显示：

该客户常买商品。

选择后必须获得：

- productId
- itemCode
- allowedUoms
- defaultUom

------

# 44. Order 提交校验

正式 Submit 前必须校验：

- Customer 存在
- customerId 有效
- 至少一个 Item
- 每个 Item 有明确 itemCode
- Qty > 0
- UOM 合法
- Rate >= 0
- Order Status 允许提交
- User 有权限

Flutter 的校验只是 UX。

后端必须重新校验。

------

# 45. 禁止静默删除错误商品

如果订单有：

苹果20箱
香蕉缺数量
葡萄10箱

点击提交：

禁止通过：

filter()

把香蕉直接删除。

必须：

阻止提交并返回明确字段错误。

------

# 46. Draft 边界（V1 已冻结）

Draft 分两层，必须区分清楚：

**编辑状态**

新增 / 编辑订单过程中，只是 Flutter 本地编辑状态。

不立即创建 ERPNext Sales Order。

AI 解析出的订单同样只是普通订单编辑页的 Draft State。

AI 解析完成不自动创建 ERPNext Sales Order。

**ERPNext Draft**

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

不静默删除该商品行。

也不建立第二套订单草稿事实。

注意：

早期「保存草稿允许任意不完整商品行」的说法已废弃。

「允许不完整」只适用于尚未落 ERP 的本地编辑状态，
不适用于创建 ERPNext Draft Sales Order。

------

# 47. Order Status 与 Payment Status

必须分离。

Order Status：

- 草稿（`docstatus=0`）
- 已提交（`docstatus=1` 且 ERP `status` 不是 Completed）
- 已完成（`docstatus=1` 且 ERP `status=Completed`）
- 已取消（`docstatus=2`）

不要伪造订单「待确认」。ERPNext 没有对应的订单状态。

Payment Status：

- 未收款
- 部分收款
- 已收款

禁止：

“待收款”

成为 Order Status。

------

# 48. 收完款不等于订单完成

全额付款：

只改变 Payment Status。

禁止自动：

Order Status → 已完成

因为业务可能：

- 钱已收
- 货还未提

------

# 49. Payment 正式身份

`paymentId` 等于 ERPNext `Payment Entry.name`。公开 API 不再返回 `erpPaymentEntryId`。

Payment 必须至少保存：

- paymentId
- customerId
- customerName
- amount
- paymentMethod
- paymentStatus
- relatedOrderId
- transactionTime

付款方式来自 ERPNext Mode of Payment，不写死微信/现金。该方式在当前 Company 没有默认账户时返回 `PAYMENT_METHOD_NOT_CONFIGURED`，不猜科目。

禁止只保存 Customer Name。

------

# 50. Payment 客户

收款客户必须：

- Customer Selector 选择
- 关联 Order 自动带入
- AI Resolver 解析

最终必须绑定：

customerId。

------

# 51. Payment 累计计算

订单 Payment Status 不能根据“本次收款”计算。

必须：

confirmedPaid =
所有该订单已提交（已到账）Payment 总额，优先使用 `Sales Order.advance_paid`

remainingToCollect =
max(orderTotal - confirmedPaid, 0)

`remainingToCollect` 是经营收款进度，不是会计应收。Draft Payment Entry 不计。

规则：

0 → 未收款

0 < paid < total → 部分收款

paid >= total → 已收款

------

# 52. 待确认 Payment

Payment：

待确认

→ 确认到账

以后必须：

重新计算订单 Payment Status。

禁止只修改 Payment 自己。

------

# 53. 补收尾款

例如：

Order Total：

2320

Confirmed Paid：

1000

系统必须知道：

remainingToCollect：

1320

点击：

补收尾款

自动预填：

1320

用户可修改。

------

# 54. 配送不是订单必经流程

农批存在：

- 自提
- 档口拿货
- 配送
- 其他方式

禁止把：

配送

写死为所有订单生命周期状态。

如有需要：

作为：

- Pickup Method
- Note
- Optional Field
- Reminder

处理。

------

# 55. 不建设催款核心工作流

允许显示：

- 当前应收
- 已收
- 未收
- 最近收款

当前阶段禁止主动加入：

- 一键催款
- 自动催款
- 催款生命周期
- Collections Workflow

除非后续明确决策。

------

# 56. Reminder

提醒属于 SaaS 增强能力。

例如：

11点半提醒我给老韩备货。

允许保存 Reminder。

Reminder 可以关联：

- Customer
- Order
- Item

但 Reminder 不是正式订单状态。

------

# 57. ASR 原则

ASR 只负责：

Voice → Text

ASR 文本不是正式身份。

例如：

韩兆亮

识别成：

韩照亮

仍必须进入 Customer Resolver。

------

# 58. AI 产品原则

AI 是：

Global Operation Layer

不是：

独立 Tab
独立聊天模块
AI 工作台

一级导航：

首页｜订单｜麦克风｜客户｜更多

------

# 59. 全局麦克风

短按：

打开 Quick Action Sheet。

长按：

立即录音。

松开：

ASR
→ AI
→ Business Action

禁止：

长按
→ 打开 AI 页
→ 再点麦克风。

------

# 60. 一级和二级页面语音一致

一级页面和：

- Order Detail
- Order Edit
- Customer Detail
- Payment Edit

等二级页面必须使用同一 Voice Controller 行为。

禁止某些页面长按直录、另一些页面只能点击。

------

# 61. Quick Action Sheet

不是聊天页面。

可以包含：

- Voice
- Text
- 开订单
- 记收款
- 查客户
- 查库存

关闭后回到原业务页面。

------

# 62. AI Action

正式 AI Intent / Action 至少支持：

- `create_order`
- `update_current_order`
- `record_payment`
- `query_customer`
- `query_inventory`

不要只设计：

AI Order。

------

# 63. create_order

例如：

老韩80果20箱、粉蕉30件。

输出必须解析到：

Customer ID

- 

明确 Items。

例如：

APPLE-80
20
箱

BANANA-FEN
30
件

不能只返回自然语言文本。

------

# 64. update_current_order

这是核心能力。

当前 Order：

苹果20箱
香蕉30件

用户：

苹果改30箱。

系统：

更新当前 Draft。

禁止：

创建新订单。

------

# 65. AI Context

AI 必须知道当前业务环境。

至少：

- currentPage
- currentOrderId
- currentCustomerId
- currentCustomerName
- currentItems

`currentItems` 不能为空壳。

正式 Flutter 开发必须把当前 Draft 明细真正传入 AI Context。

------

# 66. 当前 Draft 才是 order-edit Context

禁止在 Order Edit 页面使用：

全局上一次 selectedOrder

作为 AI Context。

必须使用：

当前正在编辑的 Draft。

------

# 67. AI 追加商品

例如：

再加10箱葡萄。

如果明确唯一：

加入当前 Draft。

如果存在多个 Variant：

返回 ambiguity。

禁止猜一个商品。

------

# 68. AI 规格歧义

例如：

苹果20箱

而企业存在：

70果
75果
80果
85果

AI 不得猜。

只询问：

苹果要哪个规格？

保留：

- Customer
- Qty
- UOM
- 其他 Items

------

# 69. AI UOM 校验

用户说：

苹果20袋

如果该 Variant 不支持：

袋

不能直接创建正式订单。

必须：

- 返回 INVALID_UOM
- 或要求用户选择合法 UOM

------

# 70. AI 局部纠错

只修不确定的字段。

禁止因为：

一个 Item 不确定

就清空整个订单。

------

# 71. AI Risk 原则

风险不只依赖 LLM confidence。

高风险包括：

- 多个合理 Customer
- 多个合理 Item
- 不合法 UOM
- 修改已提交单据
- 关键字段缺失
- 权限不足

优先：

生成可编辑 Draft

而不是所有动作都强制多一次确认。

------

# 72. 不要所有 AI 行为都确认一次

明确低风险：

自动填入 Draft。

真正歧义：

才要求用户处理。

正式 Submit：

由正常业务按钮/权限控制。

------

# 73. AI Memory 不作为用户概念展示

禁止 UI 出现：

- AI记忆
- AI学到了
- AI记住了你
- AI Memory Center

Customer Alias 等信息：

以正常业务名称展示：

常用称呼
简称

而不是“AI记忆”。

------

# 74. Knowledge Tenant 隔离

所有：

Customer Identity
Product Identity
Knowledge

必须 Tenant 私有。

禁止跨租户学习。

------

# 75. Knowledge 存储原则

结构化身份知识优先 SQL。

例如：

老韩 → Customer

八零 → Item

不要所有数据都塞 pgvector。

pgvector 用于：

真正适合语义检索的非结构化知识。

------

# 76. AI 失败降级

AI Service 故障时：

必须仍能：

- 开订单
- 查客户
- 查商品
- 查库存
- 收款

AI 不是系统生存依赖。

------

# 77. ERPNext 不可用

ERPNext 保存失败：

禁止 UI 显示“保存成功”。

应：

- 明确失败
- 保留用户编辑内容
- 支持重试

------

# 78. UI 总原则

UI 首先是：

成熟经营工具。

避免：

- 大面积渐变
- 大面积品牌色
- AI 科技感
- 卡片海洋
- Emoji
- 装饰动画
- PC 表格
- 信息极度稀疏

优先：

- 列表
- 分割线
- 清晰金额
- 高信息密度
- 大触控区
- 高扫描效率

------

# 79. 当前视觉基准

Primary：

```
#17645A
```

Background：

```
#F6F7F8
```

Surface：

```
#FFFFFF
```

Text：

```
#20262B
```

不要因为开发方便随意重新设计品牌色。

------

# 80. 移动订单商品布局

商品允许：

1～2 行。

不要强求一行。

例如：

苹果80果 · 80mm　　　　　　¥1,360 ＞
20箱 × ¥68/箱　　　上次 ¥65/箱

长名称：

允许自然占空间。

金额优先保证完整。

------

# 81. 禁止移动端 PC 表格

Order Edit 不使用：

商品｜数量｜单价｜小计

这种 PC ERP 表头。

Order Detail 正式 Flutter 实现也应尽量采用移动交易列表。

------

# 82. Bottom Sheet

所有 Selector / Editor Sheet 必须：

- App 全宽
- Safe Area
- 可滚动
- 大触控区域

禁止半屏宽 Bug。

------

# 83. 库存

库存事实来自 ERPNext。

禁止：

stock / 500

这种伪进度条。

只有存在明确预警配置才显示：

低库存。

------

# 84. 库存搜索

库存页支持：

- Product Name
- Alias
- Spec

筛选：

- 全部
- 低库存

------

# 85. 客户页

重点：

交易关系。

优先：

- Customer
- Alias
- 当前应收
- 最近订单
- 常买商品
- 历史价格
- 最近收款

不要做复杂 CRM Profile。

------

# 86. 客户指标

正式开发：

- 当前应收
- 累计交易
- 订单数

不能长期依赖前端 Mock 静态值。

必须从正式业务事实查询/派生。

------

# 87. 商品页

商品管理必须体现：

Product / Variant / UOM。

正式来源：

ERPNext。

禁止 App 自建第二套 Item 主数据。

------

# 88. API 身份原则

正式 API：

Customer 使用 ID。

Item 使用 Item Code / Variant ID。

Payment 使用 Payment ID。

Order 使用 Order ID。

名称仅用于展示。

------

# 89. API 错误

至少支持：

- CUSTOMER_NOT_FOUND
- CUSTOMER_AMBIGUOUS
- ITEM_NOT_FOUND
- ITEM_AMBIGUOUS
- INVALID_UOM
- INVALID_QUANTITY
- INVALID_RATE
- ORDER_NOT_FOUND
- ORDER_INVALID
- ORDER_STATUS_INVALID
- ORDER_CONFLICT
- PAYMENT_NOT_FOUND
- PAYMENT_INVALID
- PAYMENT_STATUS_INVALID
- PAYMENT_METHOD_NOT_CONFIGURED
- ERP_WRITE_CONFIGURATION_INCOMPLETE
- IDEMPOTENCY_CONFLICT
- IDEMPOTENCY_IN_PROGRESS
- IDEMPOTENCY_OUTCOME_UNKNOWN
- PERMISSION_DENIED
- ERP_UNAVAILABLE
- AI_UNAVAILABLE
- ASR_UNAVAILABLE

不要只有 HTTP 500。

------

# 90. API 幂等性

重要创建操作考虑：

Idempotency-Key

尤其：

- Create Order
- Submit Order
- Create Payment

禁止网络重试产生重复业务事实。

------

# 91. 并发

Order 更新应考虑：

- version
- updatedAt

等乐观并发机制。

禁止多端修改时静默覆盖。

------

# 92. Audit

重要业务写操作保存：

- Tenant
- User
- Action
- Target
- Timestamp
- AI Action ID（如果 AI 参与）
- Result

------

# 93. AI Logging

建议记录：

- Raw Input
- ASR Text
- Intent
- Entities
- Resolver Candidates
- Selected Entity
- User Correction
- Final Action
- ERP Object Reference
- Latency
- Provider / Model

遵守：

Tenant Isolation
Privacy
Lifecycle

------

# 94. 技术栈固定

Client：

Flutter

Backend：

Java 21
Spring Boot 3.x

AI：

Python
FastAPI

ERP：

ERPNext

SaaS / AI DB：

PostgreSQL

Cache：

Redis

Async：

RabbitMQ（按需）

Vector：

pgvector

Storage：

S3-compatible

Proxy：

Nginx

Deployment：

Docker first

------

# 95. 当前禁止主动引入

除非有明确必要：

禁止主动加入：

- Kafka
- Elasticsearch
- Qdrant
- Milvus
- Multi-Agent Framework
- Service Mesh
- Event Sourcing
- CQRS 全套
- 自研 Workflow Engine
- Kubernetes MVP

优先：

简单、可靠、可维护。

------

# 96. Figma 使用规则

Figma Make Prototype V1 已冻结为开发参考。

Figma Make React 代码：

仅用于：

- 页面理解
- 交互参考
- 原型逻辑参考

生产客户端：

Flutter。

禁止直接把 React 原型当正式生产代码架构。

------

# 97. Figma 与领域模型冲突

如果 Figma 与正式文档冲突：

以正式领域/API规则为准。

例如：

Figma 临时 Mock Payment 没有 customerId。

正式开发仍必须实现 customerId。

不要复制原型缺陷。

------

# 98. Prototype V1 冻结内容

开发阶段不要擅自推翻：

- 首页结构
- 一级 Bottom Navigation
- 全局麦克风
- Quick Action Sheet
- 多商品 Order
- Customer Selector
- Product / Variant Selector
- Allowed UOM
- UOM Selector
- 移动商品 1～2 行布局
- 历史成交价
- 订单搜索
- 库存搜索
- AI create_order
- AI update_current_order
- 累计收款
- \#17645A 主色

------

# 99. 测试要求

每个核心功能同时测试：

传统路径

和：

AI 路径。

不能只演示 AI。

------

# 100. 手工订单测试

至少：

- 单商品
- 多商品
- 不同 Variant
- 不同 UOM
- 小数 Qty
- 修改 Qty
- 修改 Rate
- 删除 Item
- 添加 Item
- 保存草稿创建 ERPNext Draft
- 再次保存修改同一张 Sales Order（不新建单）
- Submit
- 不完整 Item 保存草稿失败
- 不完整 Item 提交失败

------

# 101. AI 订单测试

至少：

- 明确 Customer
- Alias Customer
- ASR 错名字
- 单商品
- 多商品
- Variant 歧义
- UOM 歧义
- 非法 UOM
- 修改当前订单
- 追加当前商品
- AI 不可用降级

------

# 102. Payment 测试

至少：

订单总额：

2320

第一次：

1000

结果：

部分收款。

第二次：

1320

结果：

已收款。

Order Status：

不因付款自动完成。

------

# 103. 第一条黄金路径

手工：

订单
→ 新增
→ 选择韩兆亮
→ 添加苹果80果
→ 选择箱
→ 数量20
→ 添加香蕉粉蕉
→ 数量30
→ 保存/提交
→ ERPNext

------

# 104. 第二条黄金路径

AI：

长按麦克风

> 老韩80果20箱，粉蕉30件。

ASR
→ Resolver
→ Customer ID
→ Variant IDs
→ Order Draft
→ 普通 Order Edit
→ Submit
→ ERPNext

------

# 105. 第三条黄金路径

当前订单：

苹果20箱
香蕉30件

长按：

> 苹果改30箱。

结果：

当前 Draft：

苹果30箱
香蕉30件

禁止创建新 Order。

------

# 106. 第四条黄金路径

客户详情：

韩兆亮

老板说：

> 给他开20箱八零。

系统：

“他” → 韩兆亮

“八零” → 苹果80果

进入普通 Order Edit。

------

# 107. 第五条黄金路径

订单：

2320

已收：

1000

点击：

补收尾款

自动填：

1320

保存后：

Payment Status → 已收款

Order Status 不自动变化。

------

# 108. 当前开发优先级

优先顺序：

1. ERPNext 基础集成
2. SaaS User / Tenant / Permission
3. Customer / Item / Variant / UOM Adapter
4. 手工多商品 Order
5. Payment
6. Inventory Query
7. Flutter 核心 UI
8. ASR
9. Customer / Product Resolver
10. AI create_order
11. AI update_current_order
12. Knowledge Learning

------

# 109. 当前不优先

不要主动开发：

- 自动采购
- 自动催款
- 完整会计前端
- 大型 BI
- 复杂配送系统
- AI 自主经营
- 多 Agent
- 复杂 CRM
- 经营预测平台

------

# 110. 开发完成自检

每次较大功能完成前检查：

产品：

- 不用 AI 是否可用？
- 是否减少用户操作？
- 是否引入无意义确认？

领域：

- ERPNext 是否仍是事实源？
- Customer 是否有 customerId？
- Item 是否有 itemCode？
- 是否没有制造 ERPNext 不存在的合成主键？
- UOM 是否合法？
- Order 是否支持 items[]？
- 是否把 referencePrice 当成了成交价？
- 未落 ERP 的编辑状态是否没有产生 ERPNext Draft？

AI：

- 是否只问真正歧义？
- 是否保留确定字段？
- 是否拿到了当前 Draft Context？
- 修改当前订单是否没有新建订单？

Payment：

- 是否累计已到账？
- 是否与 Order Status 解耦？

安全：

- 是否 Tenant 隔离？
- 是否经过 Spring Boot？
- 是否经过 Adapter？

UI：

- 是否移动端友好？
- 是否没有 PC 表格？
- 是否保留传统入口？
- AI 是否没有抢业务主操作？

------

# 111. 最终开发原则

判断任何设计时，优先问：

> 它是否让农批老板更快、更清楚、更可靠地完成真实经营工作？

如果某个方案：

AI 更显眼，但业务更麻烦：

不要做。

如果：

架构更复杂，但用户没有明显收益：

不要做。

如果：

ERPNext 已经成熟解决：

不要重建。

如果：

能让老板少点一次、少输一次、少找一次，同时保持正式业务数据可靠：

优先做。

最终目标：

> 做一套农批老板真正每天会使用的经营软件，让 AI 自然地融入客户、商品、订单、收款和库存流程，而不是把 AI 贴在 ERP 表面。